package req;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import play.libs.ws.WSRequest;
import play.libs.ws.WSClient;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class SpinOffLoader {
    private final ObjectNode projectionNode;
    private final WSClient client;

    public static final int LIMIT = 50;

    public SpinOffLoader(WSClient ws) {
        client = ws;

        ObjectMapper projectionMapper = new ObjectMapper();
        ObjectNode projectionNode = projectionMapper.createObjectNode();
        projectionNode.put("cursor", 1);
        projectionNode.put("complete", 1);
        ObjectNode scratchpadsNode = projectionMapper.createObjectNode();
        scratchpadsNode.put("url", 1);
        projectionNode.putArray("scratchpads").add(scratchpadsNode);

        this.projectionNode = projectionNode;
    }

    public CompletionStage<Result<List<Long>>> load(final long programId) {
        return load(programId, null, 0, new ArrayList<>());
    }

    private CompletionStage<Result<List<Long>>> load(final long programId, final String cursor, final int page, final ArrayList<Long> previous) {
        final String apiUrl = String.format("https://www.khanacademy.org/api/internal/scratchpads/%d/top-forks", programId);

        final ObjectMapper tempMapper = new ObjectMapper();

        final WSRequest request = client.url(apiUrl)
                .addQueryParameter("casing", "camel")
                .addQueryParameter("subject", "all")
                .addQueryParameter("sort", "1")
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("limit", String.valueOf(LIMIT))
                .addQueryParameter("lang", "en")
                .addQueryParameter("_", String.valueOf(System.currentTimeMillis()));

        try {
            request.addQueryParameter("projection", tempMapper.writeValueAsString(projectionNode));
        } catch (JsonProcessingException e) {
            return CompletableFuture.completedFuture(Result.err(e));
        }

        if (cursor != null) {
            request.addQueryParameter("cursor", cursor);
        }

        return request.get()
                .thenCompose(response -> {
                    JsonNode body = response.asJson();

                    if (body.isNull() || body.get("cursor") == null || !body.get("cursor").isTextual()
                            || body.get("scratchpads") == null || !body.get("scratchpads").isArray()
                            || body.get("complete") == null || !body.get("complete").isBoolean()) {
                        return CompletableFuture.completedFuture(Result.err(new ContentNotFoundException(
                                "Value not found in JSON returned by KA API")));
                    }

                    Iterator<JsonNode> idNodes = body.get("scratchpads").elements();
                    while (idNodes.hasNext()) {
                        String[] url = idNodes.next().get("url").asText().split("/");
                        previous.add(Long.parseLong(url[url.length - 1]));
                    }

                    if (body.get("complete").asBoolean()) {
                        return CompletableFuture.completedFuture(Result.ok(previous));
                    }

                    return load(programId, body.get("cursor").asText(), page + 1, previous);
                });
    }
}
