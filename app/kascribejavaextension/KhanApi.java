package kascribejavaextension;

import com.github.scribejava.core.builder.api.DefaultApi10a;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuthConfig;

public class KhanApi extends DefaultApi10a {

    private static final String root = "https://www.khanacademy.org/api/";

    protected KhanApi() {

    }

    private static class KhanApiInstanceContainer {
        public static KhanApi instance = new KhanApi();
    }

    public static KhanApi instance() {
        return KhanApiInstanceContainer.instance;
    }

    @Override
    public String getRequestTokenEndpoint() {
        return root + "auth2/request_token";
    }

    @Override
    public String getAccessTokenEndpoint() {
        return root + "auth2/access_token";
    }

    @Override
    public String getAuthorizationUrl(OAuth1RequestToken oAuth1RequestToken) {
        return root + "auth2/authorize?oauth_token=" + oAuth1RequestToken.getToken();
    }

    @Override
    public KAOAuth10aService createService(OAuthConfig config) {
        return new KAOAuth10aService(this, config);
    }

}
