package models;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.sql.Connection;

import play.libs.Json;

import static configs.PrivateConfig.CONNECTION_STRING;
import static configs.PrivateConfig.USERNAME;
import static configs.PrivateConfig.PASSWORD;

/**
 * A class representing contests
 */
public class Contest {
	private List<Bracket> brackets = new ArrayList<Bracket>();
	private HashMap<Integer, Criterion> criteria = new HashMap<Integer, Criterion>();
	private Set<Integer> judgeIds = new HashSet<Integer>();
	private int id = -1, judgedEntryCount = -1, entryCount = -1;
	private String name = null, description = null;
	private long programId = -1;
	private Date endDate = null, dateCreated = null;
	private Integer userJudgedEntryCount = null;
	private User fetcher = null;

	public Contest() {

	}

	/**
	 * Creates a contest in the database
	 * 
	 * @return A Contest object or null if there was an issue fetching generated ids
	 * 
	 * @param name
	 *            The contest's name
	 * @param description
	 *            The contest's description
	 * @param programId
	 *            The contest's corresponding KA program id
	 * @param endDate
	 *            When the contest ends
	 * @param criteria
	 *            The criteria the contest will be judged on
	 * 
	 * @throws SQLException
	 */
	public static Contest createContest(String name, String description, long programId, Date endDate,
			List<Criterion> criteria, List<Bracket> brackets, Set<Integer> judgeIds, User user) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			Contest contest = new Contest();
			connection.setAutoCommit(false);

			contest.setName(name);
			contest.setDescription(description);
			contest.setProgramId(programId);
			contest.setEndDate(endDate);
			contest.setDateCreated(new Date());

			try (PreparedStatement insertContest = connection.prepareStatement(
					"INSERT INTO contests (name, description, program_id, end_date, date_created) VALUES (?, ?, ?, ?, ?)",
					java.sql.Statement.RETURN_GENERATED_KEYS)) {
				insertContest.setString(1, contest.getName());
				insertContest.setString(2, contest.getDescription());
				insertContest.setLong(3, contest.getProgramId());
				insertContest.setLong(4, contest.getEndDate().getTime());
				insertContest.setLong(5, contest.getDateCreated().getTime());
				insertContest.executeUpdate();
				try (ResultSet keys = insertContest.getGeneratedKeys()) {
					if (keys != null && keys.next())
						contest.setId(keys.getInt(1));
					else {
						connection.rollback();
						return null;
					}
				} catch (SQLException e) {
					connection.rollback();
					throw e;
				}
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			}

			for (Criterion criterion : criteria) {
				try (PreparedStatement insertCriterion = connection.prepareStatement(
						"INSERT INTO criteria (contest_id, name, description, weight) VALUES (?, ?, ?, ?)",
						java.sql.Statement.RETURN_GENERATED_KEYS)) {
					insertCriterion.setInt(1, contest.getId());
					insertCriterion.setString(2, criterion.getName());
					insertCriterion.setString(3, criterion.getDescription());
					insertCriterion.setInt(4, criterion.getWeight());
					insertCriterion.executeUpdate();

					try (ResultSet keys = insertCriterion.getGeneratedKeys()) {
						if (keys != null) {
							while (keys.next()) {
								int id = keys.getInt(1);
								criterion.setId(id);
								contest.getCriteria().put(id, criterion);
							}
						} else {
							connection.rollback();
							return null;
						}
					} catch (SQLException e) {
						connection.rollback();
						throw e;
					}
				}
			}
			for (Bracket bracket : brackets) {
				try (PreparedStatement insertBracket = connection.prepareStatement(
						"INSERT INTO brackets (name, contest_id) VALUES (?, ?)",
						java.sql.Statement.RETURN_GENERATED_KEYS)) {
					insertBracket.setString(1, bracket.getName());
					insertBracket.setInt(2, contest.getId());
					insertBracket.executeUpdate();

					try (ResultSet keys = insertBracket.getGeneratedKeys()) {
						if (keys != null && keys.next()) {
							int id = keys.getInt(1);
							bracket.setId(id);
							contest.getBrackets().add(bracket);
						} else {
							connection.rollback();
							return null;
						}
					} catch (SQLException e) {
						connection.rollback();
						throw e;
					}
				}
			}
			for (int judgeId : judgeIds) {
				try (PreparedStatement insertId = connection
						.prepareStatement("INSERT INTO judges (user_id, contest_id) VALUES (?, ?)")) {
					insertId.setInt(1, judgeId);
					insertId.setInt(2, contest.getId());
					insertId.executeUpdate();
				}
			}

			contest.setJudgedEntryCount(0);
			contest.setEntryCount(0);

			contest.setJudgeIds(judgeIds);

			contest.setUserJudgedEntryCount(null);
			contest.setFetcher(user);

			connection.commit();
			return contest;
		}
	}

	/**
	 * Returns how many entries a judge has judged.
	 * 
	 * @return The user's judged entry count as an Integer. Returns null if the user
	 *         is not a judge for this contest
	 * 
	 * @param user
	 *            The judge
	 * 
	 * @throws SQLException
	 */
	private Integer getUserJudgedEntryCount(User user) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement checkUser = connection.prepareStatement(
					"SELECT COUNT(*) AS cnt FROM judges WHERE judges.contest_id = ? AND judges.user_id = ?")) {
				checkUser.setInt(1, getId());
				checkUser.setInt(2, user.getId());
				try (ResultSet results = checkUser.executeQuery()) {
					if (results.next() && results.getInt("cnt") == 0) {
						return null;
					}
				}
			}
			try (PreparedStatement stmt = connection.prepareStatement(
					"SELECT COUNT(*) AS cnt FROM entries JOIN feedback ON entries.id = feedback.entry_id WHERE entries.contest_id = ? AND feedback.user_id = ?")) {
				stmt.setInt(1, getId());
				stmt.setInt(2, user.getId());
				try (ResultSet results = stmt.executeQuery()) {
					return results.next() ? results.getInt("cnt") : null;
				}
			}
		}
	}

	/**
	 * Gets a contest with the specified id
	 *
	 * @return Returns a Contest object or null if no contest with the given id
	 *         exists
	 * 
	 * @param id
	 *            The contest's id
	 * 
	 * @param user
	 *            The user fetching the contest
	 * 
	 * @throws SQLException
	 */
	public static Contest getContestById(int id, User user) throws SQLException {
		Contest contest = null;
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			contest = getContestById(connection, id, user);
		}
		return contest;
	}

	/**
	 * Gets a contest with the specified id
	 *
	 * @return Returns a Contest object or null if no contest with the given id
	 *         exists
	 *
	 * @param id
	 *            The contest's id
	 * 
	 * @param connection
	 *            The SQL connection
	 * @param user
	 *            The user fetching the contest
	 * 
	 * @throws SQLException
	 */
	public static Contest getContestById(Connection connection, int id, User user) throws SQLException {
		Contest contest = null;
		try (PreparedStatement fetchContestStmt = connection
				.prepareStatement("SELECT * FROM contests WHERE id = ? LIMIT 1")) {
			fetchContestStmt.setInt(1, id);
			try (ResultSet contestResults = fetchContestStmt.executeQuery()) {
				if (contestResults.next()) {
					contest = new Contest();
					contest.setId(contestResults.getInt("id"));
					contest.setDateCreated(new Date(contestResults.getLong("date_created")));
					contest.setEndDate(new Date(contestResults.getLong("end_date")));
					contest.setName(contestResults.getString("name"));
					contest.setDescription(contestResults.getString("description"));
					contest.setProgramId(contestResults.getLong("program_id"));
					contest.setFetcher(user);
					try (PreparedStatement fetchCriteriaStmt = connection
							.prepareStatement("SELECT * FROM criteria WHERE contest_id = ?")) {
						fetchCriteriaStmt.setInt(1, contest.getId());
						try (ResultSet criteriaResults = fetchCriteriaStmt.executeQuery()) {
							HashMap<Integer, Criterion> criteria = new HashMap<Integer, Criterion>();
							while (criteriaResults.next()) {
								Criterion criterion = new Criterion();
								criterion.setId(criteriaResults.getInt("id"));
								criterion.setName(criteriaResults.getString("name"));
								criterion.setDescription(criteriaResults.getString("description"));
								criterion.setWeight(criteriaResults.getInt("weight"));
								criteria.put(criteriaResults.getInt("id"), criterion);
							}
							contest.setCriteria(criteria);
						}
					}
					try (PreparedStatement fetchBracketsStmt = connection
							.prepareStatement("SELECT * FROM brackets WHERE contest_id = ?")) {
						fetchBracketsStmt.setInt(1, contest.getId());
						try (ResultSet bracketsResults = fetchBracketsStmt.executeQuery()) {
							List<Bracket> brackets = new ArrayList<Bracket>();
							while (bracketsResults.next()) {
								Bracket bracket = new Bracket();
								bracket.setId(bracketsResults.getInt("id"));
								bracket.setName(bracketsResults.getString("name"));
								brackets.add(bracket);
							}
							contest.setBrackets(brackets);
						}
					}
					try (PreparedStatement fetchJudgeIds = connection
							.prepareStatement("SELECT user_id FROM judges WHERE contest_id = ?")) {
						fetchJudgeIds.setInt(1, contest.getId());
						try (ResultSet judgeIdRes = fetchJudgeIds.executeQuery()) {
							Set<Integer> judgeIds = new HashSet<Integer>();
							while (judgeIdRes.next())
								judgeIds.add(judgeIdRes.getInt("user_id"));
							contest.setJudgeIds(judgeIds);
						}
					}
					try (PreparedStatement checkStmt = connection.prepareStatement("SELECT COUNT(*) AS cnt FROM (\n"
							+ "	SELECT COUNT(tbl.score) FROM (\n"
							+ "		SELECT entries.id AS entry_id, entries.program_id AS program_id, judges.user_id AS user_id, (SELECT SUM(crit_entry.score * criteria.weight) / 100) AS score FROM judges \n"
							+ "		JOIN entries ON judges.contest_id = entries.contest_id \n"
							+ "		LEFT OUTER JOIN crit_entry ON crit_entry.entry_id = entries.id AND judges.user_id = crit_entry.user_id \n"
							+ "		LEFT OUTER JOIN criteria ON crit_entry.criterion_id = criteria.id \n"
							+ "		WHERE entries.contest_id = ?\n" + "		GROUP BY entries.id, judges.user_id\n"
							+ "	) AS tbl\n" + "	GROUP BY tbl.entry_id\n" + "	HAVING COUNT(*) = COUNT(tbl.score)\n"
							+ ") AS v;")) {
						checkStmt.setInt(1, contest.getId());
						try (ResultSet checkResults = checkStmt.executeQuery()) {
							if (checkResults.next()) {
								contest.setJudgedEntryCount(checkResults.getInt("cnt"));
							}
						}
					}
					try (PreparedStatement countStmt = connection
							.prepareStatement("SELECT COUNT(*) AS cnt FROM entries WHERE entries.contest_id = ?")) {
						countStmt.setInt(1, contest.getId());
						try (ResultSet checkResults = countStmt.executeQuery()) {
							if (checkResults.next()) {
								contest.setEntryCount(checkResults.getInt("cnt"));
							}
						}
					}
					contest.setUserJudgedEntryCount(contest.getUserJudgedEntryCount(user));
				} else {
					return null;
				}
			}
		}
		return contest;
	}

	public Bracket getBracket(int id) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement fetch = connection
					.prepareStatement("SELECT name FROM brackets WHERE id = ? AND contest_id = ? LIMIT 1")) {
				fetch.setInt(1, id);
				fetch.setInt(2, getId());
				try (ResultSet results = fetch.executeQuery()) {
					if (results.next()) {
						Bracket b = new Bracket();
						b.setId(id);
						b.setName(results.getString("name"));
						return b;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Returns whether or not the contest can be judged by a user
	 *
	 * @return boolean
	 */
	public boolean isJudgeable() {
		return getEntryCount() > 0 && getJudgedEntryCount() != getEntryCount()
				&& getEndDate().getTime() < System.currentTimeMillis() && getJudgeIds().contains(getFetcher().getId());
	}

	/**
	 * Returns whether or not the user who fetched the contest can view the
	 * contest's results
	 *
	 * @return boolean
	 */
	public boolean resultsDisclosed() {
		return getFetcher().getLevel().ordinal() >= UserLevel.ADMIN.ordinal() || (getEntryCount() > 0
				&& getJudgedEntryCount() == getEntryCount() && getEndDate().getTime() < System.currentTimeMillis()
				&& getJudgeIds().contains(getFetcher().getId()));
	}

	/**
	 * Gets a list containing the contest's results
	 *
	 * @return Returns a list full of EntryFinalResult objects
	 *
	 * @param page
	 *            The offset (times the limit)
	 * @param connection
	 *            The limit
	 * 
	 * @throws SQLException
	 */

	public List<EntryFinalResult> getResults(int page, int limit) throws SQLException {
		return getResults(page, limit, null);
	}

	public List<EntryFinalResult> getResults(int page, int limit, Integer bracket) throws SQLException {
		List<EntryFinalResult> results = new ArrayList<EntryFinalResult>();
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement scoresStmt = connection
					.prepareStatement("SELECT entry_id, program_id, AVG(tbl.score) AS average FROM (\n"
							+ "	SELECT entries.id AS entry_id, entries.program_id AS program_id, judges.user_id AS user_id, (SELECT SUM(crit_entry.score * criteria.weight) / 100) AS score FROM judges \n"
							+ "	JOIN entries ON judges.contest_id = entries.contest_id \n"
							+ "	LEFT OUTER JOIN crit_entry ON crit_entry.entry_id = entries.id AND judges.user_id = crit_entry.user_id \n"
							+ "	LEFT OUTER JOIN criteria ON crit_entry.criterion_id = criteria.id \n"
							+ "	WHERE entries.contest_id = ? \n"
							+ (bracket == null ? "" : "	AND entries.bracket_id = ? \n")
							+ "	GROUP BY entries.id, judges.user_id \n" + ") AS tbl \n" + "GROUP BY tbl.entry_id \n"
							+ "HAVING COUNT(*) = COUNT(tbl.score) \n" + "ORDER BY average DESC \n" + "LIMIT ?, ?")) {
				int ind = 0;
				scoresStmt.setInt(++ind, getId());
				if (bracket != null) {
					scoresStmt.setInt(++ind, bracket);
				}
				scoresStmt.setInt(++ind, page * limit);
				scoresStmt.setInt(++ind, limit);
				try (ResultSet scores = scoresStmt.executeQuery()) {
					while (scores.next()) {
						EntryFinalResult result = new EntryFinalResult();
						result.setEntryId(scores.getInt("entry_id"));
						result.setProgramId(scores.getLong("program_id"));
						result.setResult(scores.getDouble("average"));
						results.add(result);
					}
				}
			}
		}
		return results;
	}

	/**
	 * A helper method used to execute PreparedStatments and extract Entries from
	 * their ResultSets. Used by getAllContestEntries, getRandomUnjudgedEntry, and
	 * getContestEntries
	 *
	 * @return Returns a list full of Entry objects
	 *
	 * @param fetchEntriesStmt
	 *            The statement to execute and extract info from
	 * 
	 * @throws SQLException
	 */
	private List<Entry> entryStatementHelper(PreparedStatement fetchEntriesStmt) throws SQLException {
		try (ResultSet entriesResults = fetchEntriesStmt.executeQuery()) {
			List<Entry> entries = new ArrayList<Entry>();
			while (entriesResults.next()) {
				Entry entry = new Entry();
				entry.setId(entriesResults.getInt("id"));
				entry.setProgramId(entriesResults.getLong("program_id"));

				Bracket bracket = null;
				String bracketName = entriesResults.getString("bracket_name");
				if (bracketName != null) {
					bracket = new Bracket();
					bracket.setId(entriesResults.getInt("bracket_id"));
					bracket.setName(entriesResults.getString("bracket_name"));
				}
				entry.setBracket(bracket);

				entry.setHasBeenJudged(entriesResults.getInt("has_judged") != 0);

				entries.add(entry);
			}
			return entries;
		}
	}

	/**
	 * Gets all of the contest's entries
	 *
	 * @return Returns a list full of Entry objects
	 *
	 * @param page
	 *            The starting page
	 * @param limit
	 *            The number of entries per page
	 * 
	 * @throws SQLException
	 */
	public List<Entry> getAllContestEntries(int page, int limit) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement fetchEntriesStmt = connection.prepareStatement(
					"SELECT entries.id AS id, entries.program_id AS program_id, brackets.id AS bracket_id, feedback.id IS NOT NULL AS has_judged, brackets.name AS bracket_name \n"
							+ "FROM entries LEFT OUTER JOIN brackets ON brackets.id = entries.bracket_id \n"
							+ "LEFT OUTER JOIN feedback ON feedback.entry_id = entries.id AND feedback.user_id = ? \n"
							+ "WHERE entries.contest_id = ? \n" + "LIMIT ?, ?")) {
				fetchEntriesStmt.setInt(1, getFetcher().getId());
				fetchEntriesStmt.setInt(2, getId());
				fetchEntriesStmt.setInt(3, page * limit);
				fetchEntriesStmt.setInt(4, limit);
				return entryStatementHelper(fetchEntriesStmt);
			}
		}
	}

	/**
	 * Gets a random contest entry that hasn't been judged by the user yet
	 *
	 * @return Returns an Entry object or null if the user has judged all of the
	 *         contest's entries already
	 * 
	 * @throws SQLException
	 */
	public Entry getRandomUnjudgedEntry() throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement rndStmt = connection
					.prepareStatement("SELECT FLOOR(COUNT(*) * RAND()) AS rnd FROM entries \n"
							+ "LEFT OUTER JOIN brackets ON brackets.id = entries.bracket_id \n"
							+ "LEFT OUTER JOIN feedback ON feedback.entry_id = entries.id AND feedback.user_id = ? \n"
							+ "WHERE entries.contest_id = ? AND feedback.id IS NULL")) {
				rndStmt.setInt(1, getFetcher().getId());
				rndStmt.setInt(2, getId());
				try (ResultSet rndRes = rndStmt.executeQuery()) {
					if (rndRes.next()) {
						int randIndex = rndRes.getInt("rnd");
						try (PreparedStatement getEntryStmt = connection.prepareStatement(
								"SELECT entries.id AS id, entries.program_id AS program_id, brackets.id AS bracket_id, feedback.id IS NOT NULL AS has_judged, brackets.name AS bracket_name\n"
										+ "	FROM entries LEFT OUTER JOIN brackets ON brackets.id = entries.bracket_id \n"
										+ "LEFT OUTER JOIN feedback ON feedback.entry_id = entries.id AND feedback.user_id = ? \n"
										+ "WHERE entries.contest_id = ? AND feedback.id IS NULL LIMIT 1 OFFSET ?")) {
							getEntryStmt.setInt(1, getFetcher().getId());
							getEntryStmt.setInt(2, getId());
							getEntryStmt.setInt(3, randIndex);
							List<Entry> entrySing = entryStatementHelper(getEntryStmt);
							return entrySing.size() == 0 ? null : entrySing.get(0);
						}
					} else {
						return null;
					}
				}
			}
		}
	}

	/**
	 * Gets one of the contest's entries by id
	 *
	 * @return The fetched entry. Returns null if the entry could not be found
	 *
	 * @param id
	 *            The entry id
	 * 
	 * @throws SQLException
	 */
	public Entry getEntry(int id) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement stmt = connection.prepareStatement(
					"SELECT entries.id AS id, entries.program_id AS program_id, brackets.id AS bracket_id, brackets.name AS bracket_name FROM entries LEFT OUTER JOIN brackets ON brackets.id = entries.bracket_id WHERE entries.id = ? AND entries.contest_id = ? LIMIT 1")) {
				stmt.setInt(1, id);
				stmt.setInt(2, getId());
				try (ResultSet results = stmt.executeQuery()) {
					if (results.next()) {
						Entry entry = new Entry();
						entry.setId(results.getInt("id"));
						entry.setProgramId(results.getLong("program_id"));

						Bracket bracket = null;
						String bracketName = results.getString("bracket_name");
						if (bracketName != null) {
							bracket = new Bracket();
							bracket.setId(results.getInt("bracket_id"));
							bracket.setName(bracketName);
						}

						try (PreparedStatement hasJudged = connection.prepareStatement(
								"SELECT COUNT(*) AS cnt FROM entries JOIN feedback ON feedback.entry_id = entries.id WHERE feedback.user_id = ? AND entries.id = ?")) {
							hasJudged.setInt(1, getFetcher().getId());
							hasJudged.setInt(2, entry.getId());
							try (ResultSet res = hasJudged.executeQuery()) {
								entry.setHasBeenJudged(res.next() && res.getInt("cnt") > 0);
							}
						}

						return entry;
					} else {
						return null;
					}
				}
			}
		}
	}

	/**
	 * Inserts an entry into a contest
	 *
	 * @return The inserted entry or the existing entry with the corresponding
	 *         program id. null is returned if there is an issue getting the
	 *         inserted entry's generated id
	 *
	 * @param programId
	 *            The entry's program id
	 * 
	 * @throws SQLException
	 */
	public InsertedEntry addEntry(long programId, Connection connection) throws SQLException {
		InsertedEntry entry = new InsertedEntry();
		entry.setProgramId(programId);
		try (PreparedStatement checkStmt = connection.prepareStatement("SELECT id FROM entries WHERE program_id = ?")) {
			checkStmt.setLong(1, programId);
			try (ResultSet checkRes = checkStmt.executeQuery()) {
				if (checkRes.next()) {
					entry.setId(checkRes.getInt("id"));
					entry.setIsNew(false);
					return entry;
				} else {
					try (PreparedStatement insertStmt = connection.prepareStatement(
							"INSERT INTO entries (program_id, contest_id) VALUES (?, ?)",
							java.sql.Statement.RETURN_GENERATED_KEYS)) {
						insertStmt.setLong(1, entry.getProgramId());
						insertStmt.setInt(2, getId());
						insertStmt.executeUpdate();
						try (ResultSet insertRes = insertStmt.getGeneratedKeys()) {
							if (insertRes.next()) {
								entry.setId(insertRes.getInt(1));
								entry.setIsNew(true);
								return entry;
							} else {
								return null;
							}
						}
					}
				}
			}
		}
	}

	public InsertedEntry addEntry(long programId) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			return addEntry(programId, connection);
		}
	}

	public List<InsertedEntry> addEntries(List<Long> programIds) throws SQLException {
		List<InsertedEntry> entries = new ArrayList<InsertedEntry>();
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			connection.setAutoCommit(false);
			for (long id : programIds) {
				entries.add(addEntry(id, connection));
			}
			connection.commit();
		}
		return entries;
	}

	/**
	 * Deletes one of the contest's entries
	 *
	 * @return boolean false if the entry never existed. true if the entry was
	 *         successfully deleted
	 *
	 * @param entryId
	 *            The entry's id
	 * 
	 * @throws SQLException
	 */
	public boolean deleteEntry(int id) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			connection.setAutoCommit(false);
			try (PreparedStatement checkEntry = connection
					.prepareStatement("SELECT COUNT(*) AS cnt FROM entries WHERE id = ? AND contest_id = ?")) {
				checkEntry.setInt(1, id);
				checkEntry.setInt(2, getId());
				try (ResultSet checkEntryRes = checkEntry.executeQuery()) {
					if (checkEntryRes.next() && checkEntryRes.getInt("cnt") == 0) {
						connection.rollback();
						return false;
					}
				}
			}
			try (PreparedStatement deleteEntry = connection
					.prepareStatement("DELETE FROM entries WHERE id = ? AND contest_id = ? LIMIT 1")) {
				deleteEntry.setInt(1, id);
				deleteEntry.setInt(2, getId());
				deleteEntry.executeUpdate();
			}
			try (PreparedStatement deleteFeedback = connection
					.prepareStatement("DELETE FROM feedback WHERE entry_id = ?")) {
				deleteFeedback.setInt(1, id);
				deleteFeedback.executeUpdate();
			}
			try (PreparedStatement deleteCritEntry = connection
					.prepareStatement("DELETE FROM crit_entry WHERE entry_id = ?")) {
				deleteCritEntry.setInt(1, id);
				deleteCritEntry.executeUpdate();
			}
			connection.commit();
			return true;
		}
	}

	/**
	 * Returns a list of recent contests
	 * 
	 * @param page
	 *            What page the list starts on
	 * @param limit
	 *            How many contests each page contains
	 * @param user
	 *            The user fetching the contests
	 * 
	 * @throws SQLException
	 */
	public static List<Contest> getRecentContests(int page, int limit, User user) throws SQLException {
		List<Contest> contests = new ArrayList<Contest>();
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement fetchIds = connection.prepareStatement(
					"SELECT id FROM contests WHERE ? IN (SELECT user_id FROM judges WHERE judges.contest_id = contests.id) OR ? >= ? ORDER BY date_created DESC LIMIT ?, ?")) {
				fetchIds.setInt(1, user.getId());
				fetchIds.setInt(2, user.getLevel().ordinal());
				fetchIds.setInt(3, UserLevel.ADMIN.ordinal());
				fetchIds.setInt(4, page * limit);
				fetchIds.setInt(5, limit);
				try (ResultSet idResults = fetchIds.executeQuery()) {
					while (idResults.next()) {
						int id = idResults.getInt("id");
						Contest contest = getContestById(connection, id, user);
						if (contest != null)
							contests.add(contest);
					}
				}
			}
		}
		return contests;
	}

	/**
	 * Returns a JsonNode that contains basic information about the contest
	 * 
	 * @return A JsonNode
	 */
	public JsonNode asJsonBrief() {
		ObjectNode json = Json.newObject();
		json.put("name", getName());
		json.put("description", getDescription());
		json.put("id", getId());
		json.put("programId", getProgramId());
		json.put("dateCreated", getDateCreated().getTime());
		json.put("endDate", endDate.getTime());
		json.put("judgedEntryCount", getJudgedEntryCount());
		json.put("entryCount", getEntryCount());
		return json;
	}

	/**
	 * Returns a JsonNode representing the Contest
	 * 
	 * @param user
	 *            The judge
	 * 
	 * @return A JsonNode representing the Contest
	 */
	public JsonNode asJson() {
		ObjectNode json = (ObjectNode) asJsonBrief();

		json.put("userCanJudge", isJudgeable());
		json.put("userCanViewResults", resultsDisclosed());

		ArrayNode criteria = json.putArray("criteria");
		Set<Map.Entry<Integer, Criterion>> criteriaEntries = getCriteria().entrySet();
		for (Map.Entry<Integer, Criterion> criteriaEntry : criteriaEntries)
			criteria.add(criteriaEntry.getValue().asJson());

		ArrayNode brackets = json.putArray("brackets");
		Iterator<Bracket> bracketsIter = getBrackets().iterator();
		while (bracketsIter.hasNext())
			brackets.add(bracketsIter.next().asJson());

		ArrayNode judges = json.putArray("judges");
		Iterator<Integer> judgesIter = getJudgeIds().iterator();
		while (judgesIter.hasNext())
			judges.add(judgesIter.next());

		return json;
	}

	/**
	 * Checks if a vote is valid or not
	 * 
	 * @param votes
	 *            A HashMap<Integer, Integer> containing the vote info
	 * 
	 * @return boolean Whether or not the vote is valid
	 * 
	 * @throws SQLException
	 */
	public boolean checkIfVoteIsVaild(HashMap<Integer, Integer> votes) {
		Set<Map.Entry<Integer, Integer>> voteFacets = votes.entrySet();
		return criteria.keySet().equals(votes.keySet()) && voteFacets.stream()
				.filter(e -> e.getValue() > 100 || e.getValue() < 0 || criteria.get(e.getKey()) == null).count() == 0;
	}

	/**
	 * Deletes the contest from the database
	 * 
	 * @return void
	 * 
	 * @throws SQLException
	 */
	public void delete() throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			connection.setAutoCommit(false);
			try (PreparedStatement deleteBrackets = connection
					.prepareStatement("DELETE FROM brackets WHERE contest_id = ?")) {
				deleteBrackets.setInt(1, getId());
				deleteBrackets.executeUpdate();
			}
			try (PreparedStatement deleteEntries = connection
					.prepareStatement("DELETE FROM entries WHERE contest_id = ?")) {
				deleteEntries.setInt(1, getId());
				deleteEntries.executeUpdate();
			}
			try (PreparedStatement deleteJudges = connection
					.prepareStatement("DELETE FROM judges WHERE contest_id = ?")) {
				deleteJudges.setInt(1, getId());
				deleteJudges.executeUpdate();
			}
			try (PreparedStatement deleteCritEntries = connection.prepareStatement(
					"DELETE crit_entry FROM crit_entry JOIN criteria ON crit_entry.criterion_id = criteria.id WHERE criteria.contest_id = ?")) {
				deleteCritEntries.setInt(1, getId());
				deleteCritEntries.executeUpdate();
			}
			try (PreparedStatement deleteFeedback = connection.prepareStatement(
					"DELETE feedback FROM feedback JOIN entries ON feedback.entry_id = entries.id WHERE entries.contest_id = ?")) {
				deleteFeedback.setInt(1, getId());
				deleteFeedback.executeUpdate();
			}
			try (PreparedStatement deleteCriteria = connection
					.prepareStatement("DELETE FROM criteria WHERE contest_id = ?")) {
				deleteCriteria.setInt(1, getId());
				deleteCriteria.executeUpdate();
			}
			try (PreparedStatement deleteContest = connection
					.prepareStatement("DELETE FROM contests WHERE id = ? LIMIT 1")) {
				deleteContest.setInt(1, getId());
				deleteContest.executeUpdate();
			}
			connection.commit();
		}
	}

	/* GETTERS AND SETTERS */
	public List<Bracket> getBrackets() {
		return brackets;
	}

	public void setBrackets(List<Bracket> brackets) {
		this.brackets = brackets;
	}

	public HashMap<Integer, Criterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(HashMap<Integer, Criterion> criteria) {
		this.criteria = criteria;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name.length() <= 255 ? name : name.trim().substring(0, 255);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description.length() <= 500 ? description : description.trim().substring(0, 500);
	}

	public long getProgramId() {
		return programId;
	}

	public void setProgramId(long programId) {
		this.programId = programId;
	}

	public Date getEndDate() {
		return endDate;
	}

	public void setEndDate(Date endDate) {
		this.endDate = endDate;
	}

	public Date getDateCreated() {
		return dateCreated;
	}

	public void setDateCreated(Date dateCreated) {
		this.dateCreated = dateCreated;
	}

	public Set<Integer> getJudgeIds() {
		return judgeIds;
	}

	public void setJudgeIds(Set<Integer> judgeIds) {
		this.judgeIds = judgeIds;
	}

	public int getJudgedEntryCount() {
		return judgedEntryCount;
	}

	public void setJudgedEntryCount(int unjudgedEntryCount) {
		this.judgedEntryCount = unjudgedEntryCount;
	}

	public int getEntryCount() {
		return entryCount;
	}

	public void setEntryCount(int entryCount) {
		this.entryCount = entryCount;
	}

	public int getUserJudgedEntryCount() {
		return userJudgedEntryCount;
	}

	public void setUserJudgedEntryCount(Integer userJudgedEntryCount) {
		this.userJudgedEntryCount = userJudgedEntryCount;
	}

	public User getFetcher() {
		return fetcher;
	}

	public void setFetcher(User fetcher) {
		this.fetcher = fetcher;
	}
}