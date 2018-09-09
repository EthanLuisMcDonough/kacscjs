package req;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

public class SpinOffIter implements Iterator<ArrayListExceptions<Long>> {
    private boolean isComplete = false;
    private int pageLength = 50;
    private long programId = -1;
    private String cursor = null;
    private SpinOffSort sort = SpinOffSort.TOP;
    private int page = 0;
    private CloseableHttpClient client = Http.client;

    public SpinOffIter(long programId) {
        this.programId = programId;
    }

    public SpinOffIter(long programId, int pageLength) {
        this.programId = programId;
        this.pageLength = pageLength;
    }

    public SpinOffIter(long programId, int pageLength, SpinOffSort sort) {
        this.programId = programId;
        this.pageLength = pageLength;
        this.sort = sort;
    }

    @Override
    public boolean hasNext() {
        return !isComplete;
    }

    @Override
    public ArrayListExceptions<Long> next() {
        ArrayListExceptions<Long> programIds = new ArrayListExceptions<Long>();
        try {
            ObjectMapper projectionMapper = new ObjectMapper();
            ObjectNode projectionNode = projectionMapper.createObjectNode();
            projectionNode.put("cursor", 1);
            projectionNode.put("complete", 1);
            ObjectNode scratchpadsNode = projectionMapper.createObjectNode();
            scratchpadsNode.put("url", 1);
            projectionNode.putArray("scratchpads").add(scratchpadsNode);

            URIBuilder builder = new URIBuilder().setScheme("https").setHost("www.khanacademy.org")
                    .setPath(String.format("/api/internal/scratchpads/%d/top-forks", programId))
                    .setParameter("casing", "camel").setParameter("subject", "all")
                    .setParameter("sort", String.valueOf(sort.getSortId())).setParameter("page", String.valueOf(page++))
                    .setParameter("limit", String.valueOf(pageLength)).setParameter("lang", "en")
                    .setParameter("_", String.valueOf(System.currentTimeMillis()))
                    .setParameter("projection", projectionMapper.writeValueAsString(projectionNode));

            if (cursor != null)
                builder.setParameter("cursor", cursor);

            HttpGet request = new HttpGet(builder.toString());
            try (CloseableHttpResponse res = client.execute(request)) {
                HttpEntity entity = null;
                try {
                    entity = res.getEntity();
                    try (InputStream content = entity.getContent()) {
                        ObjectMapper mapper = new ObjectMapper();
                        JsonNode json = mapper.readTree(content);
                        if (json.isNull() || json.get("cursor") == null || !json.get("cursor").isTextual()
                                || json.get("scratchpads") == null || !json.get("scratchpads").isArray()
                                || json.get("complete") == null || !json.get("complete").isBoolean()) {
                            throw new ContentNotFoundException("Value not found in JSON returned by KA API");
                        }
                        Iterator<JsonNode> idNodes = json.get("scratchpads").elements();
                        while (idNodes.hasNext()) {
                            String[] url = idNodes.next().get("url").asText().split("/");
                            programIds.add(Long.parseLong(url[url.length - 1]));
                        }
                        cursor = json.get("cursor").asText();
                        isComplete = json.get("complete").asBoolean();
                    } catch (ContentNotFoundException e) {
                        programIds.addException(e);
                    }
                } finally {
                    EntityUtils.consume(entity);
                }
            } catch (ClientProtocolException e) {
                programIds.addException(e);
            } catch (IOException e) {
                programIds.addException(e);
            }
        } catch (JsonProcessingException e) {
            programIds.addException(e);
        }
        return programIds;
    }
}
