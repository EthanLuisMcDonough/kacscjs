package controllers;

import play.libs.Json;
import play.libs.concurrent.HttpExecutionContext;
import play.mvc.Controller;
import play.mvc.Http.Request;
import play.mvc.Http.Session;
import play.mvc.Result;
import play.Logger;

import static configs.PrivateConfig.CONNECTION_STRING;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import javax.inject.Inject;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import models.User;
import models.UserLevel;
import models.Criterion;
import models.Entry;
import models.Bracket;
import models.Contest;
import models.EntryFinalResult;
import models.InsertedEntry;
import req.Http;
import req.ArrayListExceptions;
import req.SpinOffIter;

public class ContestApiController extends Controller {
	private HttpExecutionContext httpExecutionContext;
	private final CloseableHttpClient httpclient = Http.client;

	@Inject
	public ContestApiController(HttpExecutionContext ec) {
		httpExecutionContext = ec;
	}

	private Result internalServerErrorApiCallback(Throwable e) {
		Logger.error(e.getMessage(), e);
		return internalServerError(jsonMsg("Internal server error"));
	}

	private ObjectNode jsonMsg(String message) {
		ObjectNode json = Json.newObject();
		json.put("message", message);
		return json;
	}

	private Result createContest(Request request, Session session) {
		User user = User.getFromSession(session);

		if (user == null) {
			return unauthorized(jsonMsg("Unauthorized"));
		} else if (user.getLevel() != UserLevel.ADMIN) {
			return forbidden(jsonMsg("Only admins can create contests"));
		}

		JsonNode jsonBody = request.body().asJson();

		String name = jsonBody.get("name").asText().trim(), description = jsonBody.get("description").asText().trim();
		long programId = jsonBody.get("id").asLong(), endDate = jsonBody.get("endDate").asLong();

		JsonNode criteriaJson = jsonBody.path("criteria"), bracketsJson = jsonBody.path("brackets"),
				judgeIdsJson = jsonBody.path("judges");

		if (criteriaJson.isMissingNode() || bracketsJson.isMissingNode() || judgeIdsJson.isMissingNode()
				|| !bracketsJson.isArray() || !criteriaJson.isArray() || !judgeIdsJson.isArray() || programId == 0
				|| endDate == 0 || name.length() == 0 || description.length() == 0 || name.length() > 255
				|| description.length() > 500) {
			return badRequest(jsonMsg("Bad request"));
		}

		List<Criterion> criteria = new ArrayList<Criterion>();
		List<Bracket> brackets = new ArrayList<Bracket>();
		Set<Integer> judgeIds = new HashSet<Integer>();

		Iterator<JsonNode> criteriaIterator = criteriaJson.iterator();
		int criteriaCounter = 0;
		while (criteriaIterator.hasNext()) {
			JsonNode tok = criteriaIterator.next();
			if (tok.hasNonNull("name") && tok.get("name").asText().trim().length() > 0 && tok.hasNonNull("description")
					&& tok.get("description").asText().trim().length() > 0 && tok.hasNonNull("weight")) {
				Criterion c = new Criterion();
				c.setName(tok.get("name").asText().trim());
				c.setDescription(tok.get("description").asText().trim());
				c.setWeight(tok.get("weight").asInt());
				criteria.add(c);
			} else {
				return badRequest(jsonMsg(String.format("Invalid criterion provided (position %d)", criteriaCounter)));
			}
			criteriaCounter++;
		}

		if (criteria.size() == 0
				|| criteria.stream().mapToInt(c -> c.getWeight()).reduce((a, b) -> a + b).getAsInt() != 100
				|| criteria.stream().filter(e -> e.getWeight() <= 0).count() > 0) {
			return badRequest(jsonMsg("Please make sure that all of your weights add up to 100"));
		}

		Iterator<JsonNode> bracketIterator = bracketsJson.iterator();
		int bracketCounter = 0;
		while (bracketIterator.hasNext()) {
			String bracketName = bracketIterator.next().asText();
			if (bracketName.trim().length() > 0) {
				Bracket b = new Bracket();
				b.setName(bracketName.trim());
				brackets.add(b);
			} else {
				return badRequest(
						jsonMsg(String.format("Invalid bracket name provided (position %d)", bracketCounter)));
			}
			bracketCounter++;
		}

		Iterator<JsonNode> judgeIdsIterator = judgeIdsJson.iterator();
		int judgeIdCount = 0;
		while (judgeIdsIterator.hasNext()) {
			JsonNode judgeIdJsonNode = judgeIdsIterator.next();
			if (judgeIdJsonNode.isInt()) {
				int judgeId = judgeIdJsonNode.asInt();
				if (judgeIds.contains(judgeId)) {
					return badRequest(
							jsonMsg(String.format("Duplicate judge id at index %d in the judges array", judgeIdCount)));
				} else {
					judgeIds.add(judgeId);
				}
			} else {
				return badRequest(
						jsonMsg(String.format("Invalid judge id at index %d in the judges array", judgeIdCount)));
			}
			judgeIdCount++;
		}

		if (judgeIds.size() == 0) {
			return badRequest(jsonMsg("Your contest must have at least one judge"));
		}

		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING)) {
			try (PreparedStatement checkStmt = connection
					.prepareStatement("SELECT COUNT(*) AS num FROM contests WHERE program_id = ?")) {
				checkStmt.setLong(1, programId);
				try (ResultSet checkRes = checkStmt.executeQuery()) {
					if (checkRes.next()) {
						if (checkRes.getLong("num") > 0) {
							return badRequest(
									jsonMsg(String.format("A contest with program id %d already exists", programId)));
						}
					}
				}
			}
			for (int id : judgeIds) {
				try (PreparedStatement checkUsersStmt = connection
						.prepareStatement("SELECT COUNT(*) AS num FROM users WHERE id = ?")) {
					checkUsersStmt.setLong(1, id);
					try (ResultSet checkRes = checkUsersStmt.executeQuery()) {
						if (checkRes.next() && checkRes.getLong("num") == 0) {
							return badRequest(jsonMsg(String.format("A user with the id %d does not exist", id)));
						}
					}
				}
			}
		} catch (SQLException e1) {
			Logger.error("SQL error", e1);
			return internalServerError(jsonMsg("Internal server error"));
		}

		HttpGet programCheckReq = new HttpGet(
				"https://www.khanacademy.org/api/labs/scratchpads/" + programId + "?projection=%7B%22id%22%3A1%7D");
		try (CloseableHttpResponse programCheckRes = httpclient.execute(programCheckReq)) {
			try {
				int code = programCheckRes.getStatusLine().getStatusCode();
				if (code < 200 || code >= 300) {
					return badRequest(jsonMsg("Could not find a program with that id"));
				}
			} finally {
				programCheckReq.releaseConnection();
			}
		} catch (IOException e) {
			Logger.error(e.getMessage(), e);
			return internalServerError(jsonMsg("Internal server error"));
		}

		Contest contest = null;
		try {
			contest = user.createContest(name, description, programId, new Date(endDate), criteria, brackets, judgeIds);
		} catch (SQLException e) {
			Logger.error(e.getMessage(), e);
			return internalServerError(jsonMsg("Internal server error"));
		}

		return contest == null ? badRequest(jsonMsg("Bad request")) : ok(contest.asJson());
	}

	public CompletionStage<Result> createContest() {
		return CompletableFuture.supplyAsync(() -> createContest(request(), session()), httpExecutionContext.current())
				.exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> getContest(int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("You're not signed in"));
			} else if (user.getLevel() == UserLevel.REMOVED) {
				return forbidden(jsonMsg("Forbidden"));
			}

			Contest contest = null;
			try {
				contest = user.getContestById(id);
			} catch (SQLException e) {
				return internalServerErrorApiCallback(e);
			}

			if (contest == null) {
				return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
			} else if (!contest.getJudgeIds().contains(user.getId())
					&& user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden(jsonMsg("You're not a judge of this contest"));
			}

			return ok(contest.asJson());
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> setBracket(int contestId, int entryId) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized();
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden();
			}

			if (!request().hasBody()) {
				return badRequest();
			}

			JsonNode body = request().body().asJson();

			if (body == null || body.isNull()) {
				return badRequest();
			}

			JsonNode bracketJson = body.get("bracket");

			if (!bracketJson.isNull() && !bracketJson.isInt()) {
				return badRequest();
			}

			Integer bracketId = bracketJson.isNull() ? null : bracketJson.asInt();

			try {
				Contest contest = user.getContestById(contestId);
				if (contest == null) {
					return notFound();
				}

				Entry entry = contest.getEntry(entryId);
				if (entry == null) {
					return notFound();
				}

				Bracket bracket = bracketId == null ? null : contest.getBracket(bracketId);
				if (bracket == null && bracketId != null) {
					return notFound();
				}

				entry.realSetBracket(bracket);
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError();
			}

			return ok("");
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> randomEntry(int contestId) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
				return forbidden(jsonMsg("Forbidden"));
			}

			try {
				Contest contest = user.getContestById(contestId);

				if (contest == null) {
					return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
				} else if (!contest.getJudgeIds().contains(user.getId())
						&& user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
					return forbidden(jsonMsg("You're not a judge of this contest"));
				}

				Entry entry = contest.getRandomUnjudgedEntry();

				if (entry == null) {
					ObjectNode entryNode = Json.newObject();
					entryNode.putNull("id");
					entryNode.putNull("programId");
					entryNode.putNull("bracket");
					entryNode.putNull("url");
					entryNode.putNull("absoluteUrl");
					entryNode.put("judgingFinished", true);
					return ok(entryNode);
				}

				ObjectNode entryNode = (ObjectNode) entry.asJson();
				entryNode.put("url", routes.ContestUIController.entry(contestId, entry.getId()).url());
				entryNode.put("absoluteUrl",
						routes.ContestUIController.entry(contestId, entry.getId()).absoluteURL(request()));
				entryNode.put("judgingFinished", false);
				return ok(entryNode);
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> getContests(int page, int limit) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel() == UserLevel.REMOVED) {
				return forbidden(jsonMsg("Forbidden"));
			}

			try {
				Iterator<Contest> contests = Contest.getRecentContests(page, limit, user).iterator();
				ArrayNode json = Json.newArray();
				while (contests.hasNext())
					json.add(contests.next().asJsonBrief());
				return ok(json);
			} catch (SQLException e) {
				return internalServerErrorApiCallback(e);
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> getEntries(int id, int page, int limit) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
				return forbidden(jsonMsg("Forbidden"));
			}

			try {
				ArrayNode jsonEntries = Json.newArray();

				Contest contest = user.getContestById(id);

				if (contest == null) {
					return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
				} else if (!contest.getJudgeIds().contains(user.getId())
						&& user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
					return forbidden(jsonMsg("You're not a judge of this contest"));
				}

				List<Entry> entries = contest.getAllContestEntries(page, limit);

				Iterator<Entry> entryIter = entries.iterator();
				while (entryIter.hasNext())
					jsonEntries.add(entryIter.next().asJson());

				return ok(jsonEntries);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> entryScores(int id, int page, int limit) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			}

			Integer bracket = null;
			Map<String, String[]> query = request().queryString();
			String[] bracketQueryValues = query.get("bracket");
			if (bracketQueryValues != null && bracketQueryValues.length > 0) {
				try {
					bracket = Integer.valueOf(bracketQueryValues[0]);
				} catch (NumberFormatException e) {
					return badRequest(jsonMsg("Invalid bracket id"));
				}
			}

			try {
				Contest contest = user.getContestById(id);

				if (contest == null) {
					return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
				} else if (!contest.resultsDisclosed()) {
					return forbidden(jsonMsg("Forbidden"));
				}

				List<EntryFinalResult> results = contest.getResults(page, limit, bracket);
				Iterator<EntryFinalResult> resultsIter = results.iterator();

				ArrayNode resultsArray = Json.newArray();

				while (resultsIter.hasNext())
					resultsArray.add(resultsIter.next().asJson());

				return ok(resultsArray);
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> getEntry(int contestId, int entryId) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized("Unauthorized");
			} else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
				return forbidden("Forbidden");
			}

			try {
				Contest contest = user.getContestById(contestId);
				if (contest == null) {
					return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
				}

				Entry entry = contest.getEntry(entryId);
				if (entry == null) {
					return notFound(jsonMsg(
							String.format("Contest %d does not have an entry with the id %d", contestId, entryId)));
				}

				return ok(entry.asJson());
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> voteEntry(int contestId, int entryId) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized("Unauthorized");
			} else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
				return forbidden("Forbidden");
			}

			JsonNode jsonBody = request().body().asJson();

			if (jsonBody.get("feedback") == null || jsonBody.path("votes") == null) {
				return badRequest(jsonMsg("Bad request"));
			}

			String feedback = jsonBody.get("feedback").asText();

			JsonNode votesJson = jsonBody.path("votes");

			if (votesJson.isMissingNode() || !votesJson.isArray() || !jsonBody.get("feedback").isTextual()) {
				return badRequest(jsonMsg("Bad request"));
			} else if (feedback.length() > 5000) {
				return badRequest(jsonMsg("Feedback too long"));
			}

			HashMap<Integer, Integer> votes = new HashMap<Integer, Integer>();

			Iterator<JsonNode> votesIter = votesJson.iterator();
			int votesCounter = 0;
			while (votesIter.hasNext()) {
				JsonNode vote = votesIter.next();
				if (!vote.get("id").isInt() || !vote.get("score").isInt()) {
					return badRequest(jsonMsg(String.format("Invalid item in votes array at index %d", votesCounter)));
				}
				int id = vote.get("id").asInt(), score = vote.get("score").asInt();
				if (votes.containsKey(id)) {
					return badRequest(jsonMsg(String.format("Duplicate id in votes array at index %d", votesCounter)));
				}
				votes.put(id, score);
				votesCounter++;
			}

			Contest contest = null;
			try {
				contest = user.getContestById(contestId);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal Server Error"));
			}

			if (contest == null) {
				return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
			} else if (System.currentTimeMillis() < contest.getEndDate().getTime()) {
				return forbidden("You can't judge a contest entry before the contest is over");
			} else if (!contest.checkIfVoteIsVaild(votes)) {
				return badRequest(jsonMsg("Bad request (invalid votes)"));
			} else if (!contest.getJudgeIds().contains(user.getId())) {
				return forbidden(jsonMsg(
						"You don't judge this contest." + (user.getLevel().ordinal() >= UserLevel.ADMIN.ordinal()
								? "  You need to add yourself as a judge before you can vote"
								: "")));
			}

			Entry entry = null;
			try {
				entry = contest.getEntry(entryId);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal Server Error"));
			}

			if (entry == null) {
				return notFound(jsonMsg(String.format("This contest does not have an entry with the id %d", entryId)));
			}

			boolean success = false;
			try {
				success = user.voteEntry(entry, contest, votes, feedback);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal Server Error"));
			}

			return success ? ok(jsonMsg("Success")) : status(409, jsonMsg("You already voted this entry"));
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> newEntry(int id, long programId) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden(jsonMsg("Forbidden"));
			}

			Contest contest = null;
			try {
				contest = user.getContestById(id);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			if (contest == null) {
				return notFound(jsonMsg(String.format("Contest with id %d does not exist", id)));
			}

			HttpGet programCheckReq = new HttpGet(
					"https://www.khanacademy.org/api/labs/scratchpads/" + programId + "?projection=%7B%22id%22%3A1%7D");
			try (CloseableHttpResponse programCheckRes = httpclient.execute(programCheckReq)) {
				int code = programCheckRes.getStatusLine().getStatusCode();
				if (code < 200 || code >= 300) {
					return badRequest(jsonMsg("Could not find a program with that id"));
				}
			} catch (IOException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			InsertedEntry entry = null;
			try {
				entry = contest.addEntry(programId);
			} catch (SQLException e) {
				Logger.error(e.getMessage(), e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			return entry == null ? internalServerError(jsonMsg("Internal server error")) : (entry.getIsNew() ? ok(entry.asJson()) : badRequest(jsonMsg("That entry was already inserted")));
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> deleteEntry(int contestId, int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden(jsonMsg("Forbidden"));
			}

			boolean success = false;
			try {
				Contest contest = user.getContestById(contestId);
				success = contest.deleteEntry(id);
			} catch (SQLException e) {
				Logger.error("SQL error", e);
				return internalServerError(jsonMsg("Internal server error"));
			}

			return success ? ok("")
					: notFound(
							jsonMsg(String.format("An entry with id %d in contest %d does not exist", id, contestId)));
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> deleteContest(int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized();
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden();
			}

			try {
				Contest contest = user.getContestById(id);
				if (contest == null) {
					return notFound();
				}
				contest.delete();
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError();
			}

			return ok("");
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}

	public CompletionStage<Result> addAllSpinOffs(int id) {
		return CompletableFuture.supplyAsync(() -> {
			User user = User.getFromSession(session());

			if (user == null) {
				return unauthorized(jsonMsg("Unauthorized"));
			} else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
				return forbidden(jsonMsg("forbidden"));
			}

			try {
				Contest contest = user.getContestById(id);
				if (contest == null) {
					return notFound(jsonMsg("That contest doesn't exist"));
				}

				List<Long> programIds = new ArrayList<Long>();
				Iterator<ArrayListExceptions<Long>> entryFetcher = new SpinOffIter(contest.getProgramId());
				while (entryFetcher.hasNext()) {
					ArrayListExceptions<Long> iteration = entryFetcher.next();
					if (iteration.successful())
						programIds.addAll(iteration);
					else {
						Logger.error("Error", iteration.getExceptions().get(0));
						return internalServerError(jsonMsg("Internal server error"));
					}
				}

				ObjectMapper jsonMapper = new ObjectMapper();
				ArrayNode returnJson = jsonMapper.createArrayNode();
				List<InsertedEntry> insertedEntries = contest.addEntries(programIds);
				for (InsertedEntry entry : insertedEntries) {
					if (entry.getIsNew())
						returnJson.add(entry.asJson());
				}

				return ok(returnJson);
			} catch (SQLException e) {
				Logger.error("Error", e);
				return internalServerError(jsonMsg("Internal server error"));
			}
		}, httpExecutionContext.current()).exceptionally(this::internalServerErrorApiCallback);
	}
}
