package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

import com.github.scribejava.core.model.OAuth1AccessToken;
import com.github.scribejava.core.model.OAuth1RequestToken;
import com.github.scribejava.core.model.OAuthRequest;
import com.github.scribejava.core.model.Verb;

import static configs.PrivateConfig.KA_CONSUMER_KEY;
import static configs.PrivateConfig.KA_CONSUMER_SECRET;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import java.io.InputStream;
import java.security.SecureRandom;
import java.sql.SQLException;

import play.Logger;
import play.mvc.*;
import play.mvc.Http.Request;

import play.libs.concurrent.HttpExecutionContext;
import kascribejavaextension.KAOAuth10aService;
import kascribejavaextension.KAServiceBuilder;
import kascribejavaextension.KhanApi;

import java.util.Base64;

import models.User;

public class LoginController extends Controller {
	private HttpExecutionContext httpExecutionContext;
	private final SecureRandom generator = new SecureRandom();
	private final Base64.Encoder b64Encoder = Base64.getEncoder().withoutPadding();
	private final int VERIF_TOKEN_LENGTH = 64;

	@Inject
	public LoginController(HttpExecutionContext ec) {
		this.httpExecutionContext = ec;
	}

	private String genOAuthVerifToken() {
		byte[] randomBytes = new byte[(int) (VERIF_TOKEN_LENGTH * 0.75)];
		generator.nextBytes(randomBytes);
		return b64Encoder.encodeToString(randomBytes).replaceAll("\\+", "-").replaceAll("\\/", "_");
	}

	public String authURL() throws IOException, InterruptedException, ExecutionException {
		String token = genOAuthVerifToken();
		session("temp-oauth-verif-token", token);
		String oauthCallback = routes.LoginController.login(token).absoluteURL(request());

		KAOAuth10aService kaservice = (KAOAuth10aService) new KAServiceBuilder(KA_CONSUMER_KEY)
				.apiSecret(KA_CONSUMER_SECRET).callback(oauthCallback).build(KhanApi.instance());

		OAuth1RequestToken requestToken = null;
		requestToken = kaservice.getRequestToken();

		String url = kaservice.getAuthorizationUrl(requestToken);
		return url;
	}

	public User getUserFromOAuthRequestToken(String tokenPublic, String tokenSecret, String verifier)
			throws IOException, InterruptedException, ExecutionException, SQLException {
		KAOAuth10aService kaservice = (KAOAuth10aService) new KAServiceBuilder(KA_CONSUMER_KEY)
				.apiSecret(KA_CONSUMER_SECRET).build(KhanApi.instance());

		OAuth1RequestToken requestToken = new OAuth1RequestToken(tokenPublic, tokenSecret);
		OAuth1AccessToken accessToken = kaservice.getAccessToken(requestToken, verifier);

		OAuthRequest req = new OAuthRequest(Verb.GET, "https://www.khanacademy.org/api/v1/user?casing=camel");
		kaservice.signRequest(accessToken, req);

		com.github.scribejava.core.model.Response res = kaservice.execute(req);
		try (InputStream responseStream = res.getStream()) {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode json = mapper.readTree(responseStream);

			if (json.get("kaid") == null || !json.get("kaid").isTextual()) {
				return null;
			}

			User user = User.getUserByKaid(json.get("kaid").asText());

			if (json.get("nickname") != null && json.get("nickname").isTextual()) {
				user.realSetName(json.get("nickname").asText());
			}

			return user;
		}
	}

	public CompletionStage<Result> getAuthURL() {
		return CompletableFuture.supplyAsync(() -> {
			if (User.getFromSession(session()) != null) {
				return temporaryRedirect(routes.ContestUIController.contests().url());
			}
			try {
				return temporaryRedirect(authURL());
			} catch (Exception e) {
				Logger.error(e.getMessage(), e);
				throw new Error("Couldn't fetch auth url");
			}
		}, httpExecutionContext.current()).exceptionally(e -> {
			Logger.error(e.getMessage(), e);
			return internalServerError(views.html.error500.render(User.getFromSession(session())));
		});
	}

	public CompletionStage<Result> login(String token) {
		return CompletableFuture.supplyAsync(() -> {
			Request req = request();
			Map<String, String[]> query = req.queryString();
			String realToken = session("temp-oauth-verif-token");
			session().remove("temp-oauth-verif-token");
			if (query.get("oauth_token_secret") == null || query.get("oauth_token_secret").length == 0
					|| query.get("oauth_token") == null || query.get("oauth_token").length == 0
					|| query.get("oauth_verifier") == null || query.get("oauth_verifier").length == 0) {
				return badRequest(views.html.error400.render(User.getFromSession(session())));
			} else if (token == null || realToken == null || !token.equals(realToken)) {
				return unauthorized(views.html.error401.render(User.getFromSession(session())));
			}

			String tokenPublic = query.get("oauth_token")[0], tokenSecret = query.get("oauth_token_secret")[0],
					verifier = query.get("oauth_verifier")[0];

			User user = null;
			try {
				user = getUserFromOAuthRequestToken(tokenPublic, tokenSecret, verifier);
			} catch (Exception e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(views.html.error500.render(user));
			}

			if (user != null) {
				user.putInSession(session());
			}

			return temporaryRedirect(routes.ContestUIController.contests().url());
		}, httpExecutionContext.current()).exceptionally(e -> {
			Logger.error(e.getMessage(), e);
			return internalServerError(views.html.error500.render(User.getFromSession(session())));
		});
	}

	public Result logout() {
		session().clear();
		return temporaryRedirect(routes.HomeController.index());
	}
}
