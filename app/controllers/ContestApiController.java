package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import contexts.*;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import play.Logger;
import play.db.Database;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Http.Request;
import req.ArrayListExceptions;
import req.Http;
import req.SpinOffIter;

import javax.inject.Inject;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static java.util.concurrent.CompletableFuture.completedFuture;

public class ContestApiController extends Controller {
    private final DBContext dbCtx;
    private final GeneralHttpPool httpCtx;
    private final CloseableHttpClient httpclient = Http.client;
    private Database db;

    @Inject
    public ContestApiController(Database db, DBContext dbCtx, GeneralHttpPool httpCtx) {
        this.db = db;
        this.dbCtx = dbCtx;
        this.httpCtx = httpCtx;
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

    public CompletionStage<Result> createContest() {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel() != UserLevel.ADMIN) {
            return completedFuture(forbidden(jsonMsg("Only admins can create contests")));
        }

        final JsonNode jsonBody = request().body().asJson();

        final String name = jsonBody.get("name").asText().trim(),
                description = jsonBody.get("description").asText().trim();
        final long programId = jsonBody.get("id").asLong(), endDate = jsonBody.get("endDate").asLong();

        final JsonNode criteriaJson = jsonBody.path("criteria"), bracketsJson = jsonBody.path("brackets"),
                judgeIdsJson = jsonBody.path("judges");

        if (criteriaJson.isMissingNode() || bracketsJson.isMissingNode() || judgeIdsJson.isMissingNode()
                || !bracketsJson.isArray() || !criteriaJson.isArray() || !judgeIdsJson.isArray() || programId == 0
                || endDate == 0 || name.length() == 0 || description.length() == 0 || name.length() > 255
                || description.length() > 500) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        }

        final List<Bracket> brackets = new ArrayList<>();
        final Set<Integer> judgeIds = new HashSet<>();
        final Set<User> judges = new HashSet<>();

        List<Criterion> tempCriteria = null;
        try {
            tempCriteria = extractCriteria(criteriaJson);
        } catch (InvalidCriterionException | CriteriaSumOutOfBoundsException e2) {
            Logger.error("User error", e2);
            return completedFuture(badRequest(jsonMsg(e2.getMessage())));
        }

        final List<Criterion> criteria = tempCriteria;

        final Iterator<JsonNode> bracketIterator = bracketsJson.iterator();
        int bracketCounter = 0;
        while (bracketIterator.hasNext()) {
            String bracketName = bracketIterator.next().asText();
            if (bracketName.trim().length() > 0) {
                Bracket b = new Bracket();
                b.setName(bracketName.trim());
                brackets.add(b);
            } else {
                return completedFuture(badRequest(jsonMsg(String.format("Invalid bracket name provided (position %d)",
                        bracketCounter))));
            }
            bracketCounter++;
        }

        final Iterator<JsonNode> judgeIdsIterator = judgeIdsJson.iterator();
        int judgeIdCount = 0;
        while (judgeIdsIterator.hasNext()) {
            JsonNode judgeIdJsonNode = judgeIdsIterator.next();
            if (judgeIdJsonNode.isInt()) {
                int judgeId = judgeIdJsonNode.asInt();
                if (judgeIds.contains(judgeId)) {
                    return completedFuture(badRequest(jsonMsg(String.format(
                            "Duplicate judge id at index %d in the judges array", judgeIdCount))));
                } else {
                    judgeIds.add(judgeId);
                }
            } else {
                return completedFuture(badRequest(jsonMsg(String.format(
                        "Invalid judge id at index %d in the judges array", judgeIdCount))));
            }
            judgeIdCount++;
        }

        if (judgeIds.size() == 0) {
            return completedFuture(badRequest(jsonMsg("Your contest must have at least one judge")));
        }

        return CompletableFuture.supplyAsync(() -> {
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

            try (Connection connection = db.getConnection(true)) {
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
                            .prepareStatement("SELECT id, kaid, level, name FROM users WHERE id = ? LIMIT 1")) {
                        checkUsersStmt.setInt(1, id);
                        try (ResultSet checkRes = checkUsersStmt.executeQuery()) {
                            if (checkRes.next()) {
                                User judge = new User();
                                judge.setId(checkRes.getInt("id"));
                                judge.setKaid(checkRes.getString("kaid"));
                                judge.setName(checkRes.getString("name"));
                                judge.setLevel(UserLevel.values()[checkRes.getInt("level")]);
                                judges.add(judge);
                            } else {
                                return badRequest(jsonMsg(String.format("A user with the id %d does not exist", id)));
                            }
                        }
                    }
                }

                Contest contest = user.createContest(name, description, programId, new Date(endDate), criteria,
                        brackets, judges, connection);
                return contest == null ? badRequest(jsonMsg("Bad request")) : ok(contest.asJson());
            } catch (SQLException e) {
                Logger.error(e.getMessage(), e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, httpCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> getContest(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("You're not signed in")));
        } else if (user.getLevel() == UserLevel.REMOVED) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
                } else if (!contest.getJudges().contains(user) && user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
                    return forbidden(jsonMsg("You're not a judge of this contest"));
                }

                return ok(contest.asJson());
            } catch (SQLException e) {
                return internalServerErrorApiCallback(e);
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> setBracket(int contestId, int entryId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        if (!request().hasBody()) {
            return completedFuture(badRequest());
        }

        final JsonNode body = request().body().asJson();

        if (body == null || body.isNull()) {
            return completedFuture(badRequest());
        }

        final JsonNode bracketJson = body.get("bracket");

        if (!bracketJson.isNull() && !bracketJson.isInt()) {
            return completedFuture(badRequest());
        }

        final Integer bracketId = bracketJson.isNull() ? null : bracketJson.asInt();

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection()) {
                Contest contest = user.getContestById(contestId, connection);
                if (contest == null) {
                    return notFound();
                }

                Entry entry = contest.getEntry(entryId, connection);
                if (entry == null) {
                    return notFound();
                }

                Bracket bracket = bracketId == null ? null : contest.getBracket(bracketId, connection);
                if (bracket == null && bracketId != null) {
                    return notFound();
                }

                entry.realSetBracket(bracket, connection);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError();
            }

            return ok("");
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> randomEntry(int contestId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        final Request req = request();

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(contestId, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
                } else if (!contest.getJudges().contains(user)
                        && user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
                    return forbidden(jsonMsg("You're not a judge of this contest"));
                }

                Entry entry = contest.getRandomUnjudgedEntry(connection);

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
                        routes.ContestUIController.entry(contestId, entry.getId()).absoluteURL(req));
                entryNode.put("judgingFinished", false);
                return ok(entryNode);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> addBracket(int contestId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        final JsonNode body = request().body().asJson();
        if (body == null || body.get("name") == null || !body.get("name").isTextual()) {
            return completedFuture(badRequest(jsonMsg("Invalid name")));
        }

        final String name = body.get("name").asText();

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(contestId, connection);

                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                return ok(contest.addBracket(name, connection).asJson());
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> removeBracket(int contestId, int bracketId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(contestId, connection);

                if (contest == null) {
                    return notFound();
                }

                contest.deleteBracket(bracketId, connection);

                return ok("");
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError();
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> getContests(int page, int limit) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel() == UserLevel.REMOVED) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection()) {
                Iterator<Contest> contests = Contest.getRecentContests(page, limit, user, connection).iterator();
                ArrayNode json = Json.newArray();

                while (contests.hasNext())
                    json.add(contests.next().asJsonBrief());

                return ok(json);
            } catch (SQLException e) {
                return internalServerErrorApiCallback(e);
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> getEntries(int id, int page, int limit) {
        User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                ArrayNode jsonEntries = Json.newArray();

                Contest contest = user.getContestById(id, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
                } else if (!contest.getJudges().contains(user)
                        && user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
                    return forbidden(jsonMsg("You're not a judge of this contest"));
                }

                List<Entry> entries = contest.getAllContestEntries(page, limit, connection);

                Iterator<Entry> entryIter = entries.iterator();

                while (entryIter.hasNext())
                    jsonEntries.add(entryIter.next().asJson());

                return ok(jsonEntries);
            } catch (SQLException e) {
                Logger.error(e.getMessage(), e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> entryScores(int id, int page, int limit) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        }

        Integer bracket = null;
        final Map<String, String[]> query = request().queryString();
        final String[] bracketQueryValues = query.get("bracket");

        if (bracketQueryValues != null && bracketQueryValues.length > 0) {
            try {
                bracket = Integer.valueOf(bracketQueryValues[0]);
            } catch (NumberFormatException e) {
                return completedFuture(badRequest(jsonMsg("Invalid bracket id")));
            }
        }

        final Integer brack = bracket;

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                final Contest contest = user.getContestById(id, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", id)));
                } else if (!contest.resultsDisclosed()) {
                    return forbidden(jsonMsg("Forbidden"));
                }

                final List<EntryFinalResult> results = contest.getResults(page, limit, brack, connection);
                final Iterator<EntryFinalResult> resultsIter = results.iterator();

                final ArrayNode resultsArray = Json.newArray();

                while (resultsIter.hasNext())
                    resultsArray.add(resultsIter.next().asJson());

                return ok(resultsArray);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> getEntry(int contestId, int entryId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized("Unauthorized"));
        } else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
            return completedFuture(forbidden("Forbidden"));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                final Contest contest = user.getContestById(contestId, connection);
                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
                }

                final Entry entry = contest.getEntry(entryId, connection);
                if (entry == null) {
                    return notFound(jsonMsg(
                            String.format("Contest %d does not have an entry with the id %d", contestId, entryId)));
                }

                return ok(entry.asJson());
            } catch (SQLException e) {
                Logger.error(e.getMessage(), e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> voteEntry(int contestId, int entryId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized("Unauthorized"));
        } else if (user.getLevel().ordinal() < UserLevel.MEMBER.ordinal()) {
            return completedFuture(forbidden("Forbidden"));
        }

        JsonNode jsonBody = request().body().asJson();

        if (jsonBody.get("feedback") == null || jsonBody.path("votes") == null) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        }

        String feedback = jsonBody.get("feedback").asText();

        JsonNode votesJson = jsonBody.path("votes");

        if (votesJson.isMissingNode() || !votesJson.isArray() || !jsonBody.get("feedback").isTextual()) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        } else if (feedback.length() > 5000) {
            return completedFuture(badRequest(jsonMsg("Feedback too long")));
        }

        HashMap<Integer, Integer> votes = new HashMap<>();

        Iterator<JsonNode> votesIter = votesJson.iterator();
        int votesCounter = 0;
        while (votesIter.hasNext()) {
            JsonNode vote = votesIter.next();
            if (!vote.get("id").isInt() || !vote.get("score").isInt()) {
                return completedFuture(badRequest(jsonMsg(String.format("Invalid item in votes array at index %d",
                        votesCounter))));
            }
            int id = vote.get("id").asInt(), score = vote.get("score").asInt();
            if (votes.containsKey(id)) {
                return completedFuture(badRequest(jsonMsg(String.format("Duplicate id in votes array at index %d",
                        votesCounter))));
            }
            votes.put(id, score);
            votesCounter++;
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(contestId, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("A contest with the id %d does not exist", contestId)));
                } else if (System.currentTimeMillis() < contest.getEndDate().getTime()) {
                    return forbidden("You can't judge a contest entry before the contest is over");
                } else if (!contest.checkIfVoteIsVaild(votes)) {
                    return badRequest(jsonMsg("Bad request (invalid votes)"));
                } else if (!contest.getJudges().contains(user)) {
                    return forbidden(jsonMsg(
                            "You don't judge this contest." + (user.getLevel().ordinal() >= UserLevel.ADMIN.ordinal()
                                    ? "  You need to add yourself as a judge before you can vote"
                                    : "")));
                } else if (!contest.isJudgeable()) {
                    return badRequest(jsonMsg("You can't judge this contest at the moment"));
                }

                Entry entry = contest.getEntry(entryId, connection);

                if (entry == null) {
                    return notFound(jsonMsg(String.format("This contest does not have an entry with the id %d", entryId)));
                }

                return user.voteEntry(entry, contest, votes, feedback, connection) ?
                        ok(jsonMsg("Success")) : status(409, jsonMsg("You already voted this entry"));
            } catch (SQLException e) {
                Logger.error(e.getMessage(), e);
                return internalServerError(jsonMsg("Internal Server Error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> newEntry(int id, long programId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
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

            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);

                if (contest == null) {
                    return notFound(jsonMsg(String.format("Contest with id %d does not exist", id)));
                }

                InsertedEntry entry = contest.addEntry(programId, connection);

                Logger.error(entry.asJson().toString());

                return entry == null ? internalServerError(jsonMsg("Internal server error"))
                        : (entry.getIsNew() ? ok(entry.asJson()) : badRequest(jsonMsg("That entry was already inserted")));
            } catch (SQLException e) {
                Logger.error(e.getMessage(), e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, httpCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> deleteEntry(int contestId, int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(false)) {
                Contest contest = user.getContestById(contestId, connection);
                return contest.deleteEntry(id, connection) ? ok("")
                        : notFound(
                        jsonMsg(String.format("An entry with id %d in contest %d does not exist", id, contestId)));
            } catch (SQLException e) {
                Logger.error("SQL error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> deleteContest(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return  completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(false)) {
                Contest contest = user.getContestById(id, connection);
                if (contest == null) {
                    return notFound();
                }
                contest.delete(connection);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError();
            }

            return ok("");
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> addAllSpinOffs(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);
                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                List<Long> programIds = new ArrayList<>();
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
                List<InsertedEntry> insertedEntries = contest.addEntries(programIds, connection);
                for (InsertedEntry entry : insertedEntries) {
                    if (entry.getIsNew())
                        returnJson.add(entry.asJson());
                }

                return ok(returnJson);
            } catch (SQLException e) {
                Logger.error("Error", e);
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, httpCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> replaceCriteria(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        final JsonNode body = request().body().asJson();
        if (body == null || !body.isArray()) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);
                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                List<Criterion> criteria = extractCriteria(body);

                contest.replaceCriteria(criteria, connection);

                return ok(jsonMsg("Success"));
            } catch (SQLException e) {
                Logger.error("error", e);
                return internalServerError(jsonMsg("Internal server error"));
            } catch (InvalidCriterionException | CriteriaSumOutOfBoundsException e) {
                Logger.error("User error", e);
                return badRequest(jsonMsg(e.getMessage()));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    private class InvalidCriterionException extends NullPointerException {
        private static final long serialVersionUID = 1L;

        public InvalidCriterionException(String msg) {
            super(msg);
        }
    }

    private class CriteriaSumOutOfBoundsException extends Exception {
        private static final long serialVersionUID = 1L;

        public CriteriaSumOutOfBoundsException(String msg) {
            super(msg);
        }
    }

    private List<Criterion> extractCriteria(JsonNode critJson)
            throws InvalidCriterionException, CriteriaSumOutOfBoundsException {
        List<Criterion> criteria = new ArrayList<>();
        Iterator<JsonNode> criteriaIterator = critJson.iterator();
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
                throw new InvalidCriterionException(
                        String.format("Invalid criterion provided (position %d)", criteriaCounter));
            }
            criteriaCounter++;
        }

        if (criteria.size() == 0
                || criteria.stream().mapToInt(c -> c.getWeight()).reduce((a, b) -> a + b).getAsInt() != 100
                || criteria.stream().filter(e -> e.getWeight() <= 0).count() > 0) {
            throw new CriteriaSumOutOfBoundsException("Please make sure that all of your weights add up to 100");
        }

        return criteria;
    }

    public CompletionStage<Result> deleteJudge(int contestId, int judgeUserId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized());
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden());
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(contestId, connection);
                if (contest == null) {
                    return notFound();
                }

                User judge = User.getUserById(judgeUserId, connection);
                if (judge == null) {
                    return notFound();
                }

                contest.deleteJudge(judge, connection);

                return ok("");
            } catch (SQLException e) {
                return internalServerError();
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> addJudge(int contestId, int judgeUserId) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                final Contest contest = user.getContestById(contestId, connection);
                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                final User judge = User.getUserById(judgeUserId, connection);
                if (judge == null) {
                    return notFound(jsonMsg("That user doesn't exist"));
                }

                return contest.addJudge(judge, connection) ? ok(judge.asJson()) :
                        badRequest(jsonMsg("That user already judges this contest"));
            } catch (SQLException e) {
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> basicInfo(int id) {
        final User user = User.getFromSession(session());

        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        final JsonNode body = request().body().asJson();
        if (body.get("name") == null || body.get("description") == null || !body.get("name").isTextual()
                || !body.get("description").isTextual()) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        }

        final String name = body.get("name").asText(), description = body.get("description").asText();

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);
                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                contest.realSetNameDesc(name, description, connection);

                return ok(jsonMsg("Success"));
            } catch (SQLException e) {
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }

    public CompletionStage<Result> editEndDate(int id) {
        final User user = User.getFromSession(session());
        if (user == null) {
            return completedFuture(unauthorized(jsonMsg("Unauthorized")));
        } else if (user.getLevel().ordinal() < UserLevel.ADMIN.ordinal()) {
            return completedFuture(forbidden(jsonMsg("Forbidden")));
        }

        final JsonNode body = request().body().asJson();
        if (body.get("endDate") == null || !body.get("endDate").isNumber()) {
            return completedFuture(badRequest(jsonMsg("Bad request")));
        }

        final Date endDate = new Date(body.get("endDate").asLong());

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = db.getConnection(true)) {
                Contest contest = user.getContestById(id, connection);
                if (contest == null) {
                    return notFound(jsonMsg("That contest doesn't exist"));
                }

                contest.realSetEndDate(endDate, connection);

                return ok(jsonMsg("Success"));
            } catch (SQLException e) {
                return internalServerError(jsonMsg("Internal server error"));
            }
        }, dbCtx).exceptionally(this::internalServerErrorApiCallback);
    }
}
