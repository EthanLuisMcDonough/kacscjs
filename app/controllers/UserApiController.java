package controllers;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

import javax.inject.Inject;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.User;
import models.UserLevel;
import play.Logger;
import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Result;

public class UserApiController extends Controller {
	private HttpExecutionContext httpExecutionContext;
	private Pattern kaidRegex = Pattern.compile("kaid_\\d{20,30}");
	private final CloseableHttpClient httpclient = req.Http.client;

	@Inject
	public UserApiController(HttpExecutionContext ec) {
		httpExecutionContext = ec;
	}

	private ObjectNode jsonMsg(String message) {
		ObjectNode json = Json.newObject();
		json.put("message", message);
		return json;
	}

	private Result internalServerErrorApiCallback(Throwable e) {
		Logger.error(e.getMessage(), e);
		return internalServerError(jsonMsg("Internal server error"));
	}

	public CompletionStage<Result> getUsers(int page, int limit) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() <= UserLevel.REMOVED.ordinal()) {
				return forbidden(jsonMsg("Forbidden"));
			}

			List<User> users = new ArrayList<User>();
			try {
				users = User.getAllUsers(page, limit);
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			ArrayNode usersJson = Json.newArray();
			Iterator<User> userIter = users.iterator();
			while (userIter.hasNext())
				usersJson.add(userIter.next().asJson());

			return ok(usersJson);
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> createUser(String kaid) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel() != UserLevel.ADMIN) {
				return forbidden(jsonMsg("Forbidden"));
			}

			if (!kaidRegex.matcher(kaid).matches()) {
				return badRequest(jsonMsg("Invalid kaid"));
			}

			String name = "";

			HttpGet programCheckReq = new HttpGet(
					String.format("https://www.khanacademy.org/api/internal/user/profile?kaid=%s", kaid));
			try (CloseableHttpResponse programCheckRes = httpclient.execute(programCheckReq)) {
				int code = programCheckRes.getStatusLine().getStatusCode();

				if (code < 200 || code >= 300) {
					return badRequest(jsonMsg("Could not find a user with that kaid"));
				}

				HttpEntity entity = programCheckRes.getEntity();
				try (InputStream body = entity.getContent()) {
					ObjectMapper mapper = new ObjectMapper();
					JsonNode json = mapper.readTree(body);
					if (json.isNull()) {
						return badRequest(jsonMsg("Could not find a user with that kaid"));
					} else {
						name = json.get("nickname").asText();
					}
				} finally {
					EntityUtils.consume(entity);
				}
			} catch (IOException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			try {
				User newUser = user.createUser(name, kaid, UserLevel.MEMBER);
				return newUser == null ? badRequest(jsonMsg("That user already exists")) : ok(newUser.asJson());
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}
	
	public CompletionStage<Result> removeUser(int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized();
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden();
			}

			try {
				User userToRemove = User.getUserById(id);
				if(userToRemove == null)
					return notFound();
				if (userToRemove.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
					user.setOtherUserLevel(userToRemove, UserLevel.REMOVED);
				} else 
					return forbidden();
			} catch (SQLException e) {
				Logger.error("Error", e);
			}
			
			return (Result)ok();
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}
	
	public CompletionStage<Result> promoteUser(int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized();
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden();
			}

			try {
				User userToRemove = User.getUserById(id);
				if(userToRemove == null)
					return notFound();
				if (userToRemove.getLevel().ordinal() < UserLevel.ADMIN.ordinal())
					user.setOtherUserLevel(userToRemove, UserLevel.ADMIN);
				else 
					return badRequest();
			} catch (SQLException e) {
				Logger.error("Error", e);
			}
			
			return (Result)ok();
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	} 
}
