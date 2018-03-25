package models;

import static configs.PrivateConfig.CONNECTION_STRING;
import static configs.PrivateConfig.USERNAME;
import static configs.PrivateConfig.PASSWORD;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.Json;
import play.mvc.Http.Session;

public class User {
	private String kaid = null;
	private int id = -1;
	private UserLevel level = UserLevel.REMOVED;
	private String name = null;

	public User() {
	}

	/**
	 * Checks if a two user objects are the same user (by checking if they have the
	 * same id)
	 * 
	 * @return true if both users are the same user
	 * 
	 * @param user
	 *            The user
	 */
	@Override
	public boolean equals(Object user) {
		return user instanceof User && getId() == ((User) user).getId();
	}

	@Override
	public int hashCode() {
		return Objects.hash(id);
	}

	public Contest getContestById(Connection connection, int id) throws SQLException {
		return Contest.getContestById(connection, id, this);
	}

	public Contest getContestById(int id) throws SQLException {
		return Contest.getContestById(id, this);
	}

	public Contest createContest(String name, String description, long programId, Date endDate,
			List<Criterion> criteria, List<Bracket> brackets, Set<User> judges) throws SQLException {
		return Contest.createContest(name, description, programId, endDate, criteria, brackets, judges, this);
	}

	public void realSetName(String name) throws SQLException {
		name = name.trim();
		name = name.length() > 255 ? name.substring(0, 255) : name;

		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement stmt = connection
					.prepareStatement("UPDATE users SET name = ? WHERE id = ? LIMIT 1")) {
				stmt.setString(1, name);
				stmt.setInt(2, getId());
				stmt.executeUpdate();
			}
		}

		setName(name);
	}

	/**
	 * Sets another user's level
	 * 
	 * @return void
	 * 
	 * @param user
	 *            The user who's level is to be set
	 * @param newLevel
	 *            The user's new level
	 * 
	 * @throws SQLException
	 */
	public void setOtherUserLevel(User user, UserLevel newLevel) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			connection.setAutoCommit(false);
			try (PreparedStatement stmt = connection
					.prepareStatement("UPDATE users SET level = ? WHERE id = ? LIMIT 1")) {
				stmt.setInt(1, newLevel.ordinal());
				stmt.setInt(2, user.getId());
				stmt.executeUpdate();
				user.setLevel(newLevel);
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			}
			connection.commit();
		} catch (SQLException e) {
			throw e;
		}
	}

	public boolean voteEntry(Entry entry, Contest contest, HashMap<Integer, Integer> votes, String feedback)
			throws SQLException {
		feedback = feedback.trim();
		feedback = feedback.length() > 5000 ? feedback.substring(0, 5000) : feedback;
		final int entryId = entry.getId();
		Set<Map.Entry<Integer, Integer>> voteFacets = votes.entrySet();
		final Date cast = new Date();

		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			connection.setAutoCommit(false);

			try (PreparedStatement checkFeedback = connection
					.prepareStatement("SELECT COUNT(*) AS cnt FROM feedback WHERE user_id = ? AND entry_id = ?")) {
				checkFeedback.setInt(1, getId());
				checkFeedback.setInt(2, entry.getId());
				try (ResultSet results = checkFeedback.executeQuery()) {
					if (results.next() && results.getLong("cnt") > 0)
						return false;
				}
			}

			for (Map.Entry<Integer, Integer> e : voteFacets) {
				try (PreparedStatement insertEntry = connection.prepareStatement(
						"INSERT INTO crit_entry (criterion_id, score, user_id, date_cast, entry_id) VALUES (?, ?, ?, ?, ?)")) {
					insertEntry.setInt(1, e.getKey());
					insertEntry.setInt(2, e.getValue());
					insertEntry.setInt(3, getId());
					insertEntry.setLong(4, cast.getTime());
					insertEntry.setInt(5, entryId);
					insertEntry.executeUpdate();
				} catch (SQLException exc) {
					connection.rollback();
					throw exc;
				}
			}

			try (PreparedStatement insertFeedback = connection.prepareStatement(
					"INSERT INTO feedback (user_id, entry_id, comment, date_written) VALUES (?, ?, ?, ?)")) {
				insertFeedback.setInt(1, getId());
				insertFeedback.setInt(2, entryId);
				insertFeedback.setString(3, feedback);
				insertFeedback.setLong(4, cast.getTime());
				insertFeedback.executeUpdate();
			} catch (SQLException e) {
				connection.rollback();
				throw e;
			}

			connection.commit();
			return true;
		}
	}

	/**
	 * Gets a user from the database
	 * 
	 * @return A User object or null if a user with that id does not exists
	 * 
	 * @param type
	 *            The id's type
	 * @param id
	 *            The user's id
	 * 
	 * @throws SQLException
	 */
	private static User getUser(UserIdType type, String id) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			User user = null;
			try (PreparedStatement stmt = connection
					.prepareStatement(String.format("SELECT * FROM users WHERE %s = ? LIMIT 1", type.toString()))) {
				if (type == UserIdType.KAID)
					stmt.setString(1, id);
				else
					stmt.setInt(1, Integer.parseInt(id));
				try (ResultSet results = stmt.executeQuery()) {
					if (results.next()) {
						user = new User();
						user.setName(results.getString("name"));
						user.setId(results.getInt("id"));
						user.setKaid(results.getString("kaid"));
						user.setLevel(UserLevel.values()[results.getInt("level")]);
					}
				}
			}
			return user;
		}
	}

	/**
	 * Gets a user with the specified KAID
	 * 
	 * @return A User object or null if no user with the specified KAID exists
	 * 
	 * @param kaid
	 *            The user's KAID
	 * 
	 * @throws SQLException
	 */
	public static User getUserByKaid(String kaid) throws SQLException {
		return getUser(UserIdType.KAID, kaid);
	}

	/**
	 * Gets a user with the specified ID
	 * 
	 * @return A User object or null if no user with the specified ID exists
	 * 
	 * @param id
	 *            The user's ID
	 * 
	 * @throws SQLException
	 */
	public static User getUserById(int id) throws SQLException {
		return getUser(UserIdType.ID, String.valueOf(id));
	}

	/**
	 * Gets a user from a Play session
	 * 
	 * @return A User object or null of the session does not contain sufficient user
	 *         data
	 * 
	 * @param session
	 *            The play session that the user is to be extracted from
	 */
	public static User getFromSession(Session session) {
		User user = null;

		String sesId = session.get("user-id"), sesKaid = session.get("user-kaid"), sesName = session.get("user-name"),
				sesLevel = session.get("user-level");

		if (sesId != null && sesKaid != null && sesName != null && sesLevel != null) {
			user = new User();
			user.setId(Integer.parseInt(sesId));
			user.setKaid(sesKaid);
			user.setName(sesName);
			user.setLevel(UserLevel.valueOf(sesLevel));
		}

		return user;
	}

	/**
	 * Gets all non-removed users from a database
	 * 
	 * @return A List of users
	 * 
	 * @throws SQLException
	 */
	public static List<User> getAllUsers(int page, int limit) throws SQLException {
		List<User> users = new ArrayList<User>();
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement stmt = connection
					.prepareStatement("SELECT * FROM users WHERE level > ? LIMIT ?, ?")) {
				stmt.setInt(1, UserLevel.REMOVED.ordinal());
				stmt.setInt(2, page * limit);
				stmt.setInt(3, limit);
				try (ResultSet res = stmt.executeQuery()) {
					while (res.next()) {
						User user = new User();
						user.setId(res.getInt("id"));
						user.setKaid(res.getString("kaid"));
						user.setLevel(UserLevel.values()[res.getInt("level")]);
						user.setName(res.getString("name"));
						users.add(user);
					}
				}
			}
		}
		return users;
	}

	/**
	 * Creates a user
	 * 
	 * @return The created user or null if the user already exists
	 * 
	 * @param connection
	 *            The SQL connection that will be used
	 * @param name
	 *            The user's name
	 * @param kaid
	 *            The user's kaid
	 * @param level
	 *            The user's level
	 * 
	 * @throws SQLException
	 */
	public User createUser(Connection connection, String name, String kaid, UserLevel level) throws SQLException {
		User user = new User();
		user.setKaid(kaid);
		user.setName(name);
		user.setLevel(level);

		try (PreparedStatement checkStmt = connection
				.prepareStatement("SELECT COUNT(*) AS cnt FROM users WHERE level > ? AND kaid = ? LIMIT 1")) {
			checkStmt.setInt(1, UserLevel.REMOVED.ordinal());
			checkStmt.setString(2, user.getKaid());
			try (ResultSet results = checkStmt.executeQuery()) {
				if (results.next() && results.getInt("cnt") > 0) {
					return null;
				}
			}
		}

		try (PreparedStatement checkStmt = connection
				.prepareStatement("SELECT id, level FROM users WHERE kaid = ? AND level = ? LIMIT 1")) {
			checkStmt.setString(1, user.getKaid());
			checkStmt.setInt(2, UserLevel.REMOVED.ordinal());
			try (ResultSet results = checkStmt.executeQuery()) {
				if (results.next()) {
					user.setId(results.getInt("id"));
					try (PreparedStatement update = connection
							.prepareStatement("UPDATE users SET level = ? WHERE id = ? LIMIT 1")) {
						update.setInt(1, level.ordinal());
						update.setInt(2, user.getId());
						update.executeUpdate();
						return user;
					}
				}
			}
		}

		try (PreparedStatement stmt = connection.prepareStatement(
				"INSERT INTO users (kaid, level, name) VALUES (?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
			stmt.setString(1, user.getKaid());
			stmt.setInt(2, user.getLevel().ordinal());
			stmt.setString(3, user.getName());
			stmt.executeUpdate();

			try (ResultSet keys = stmt.getGeneratedKeys()) {
				if (keys != null && keys.next())
					user.setId(keys.getInt(1));
			}
		}

		return user;
	}

	/**
	 * Creates a user
	 * 
	 * @return The created user
	 * 
	 * @param name
	 *            The user's name
	 * @param kaid
	 *            The user's kaid
	 * @param level
	 *            The user's level
	 * 
	 * @throws SQLException
	 */
	public User createUser(String name, String kaid, UserLevel level) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			return createUser(connection, name, kaid, level);
		}
	}

	/**
	 * Puts user data into a Play session
	 * 
	 * @return void
	 * 
	 * @param session
	 *            The Play session that the user data is to be loaded into
	 */
	public void putInSession(Session session) {
		session.put("user-id", getId() + "");
		session.put("user-kaid", getKaid());
		session.put("user-name", getName());
		session.put("user-level", getLevel().name());
	}

	/**
	 * Returns a JsonNode representing the User
	 * 
	 * @return A JsonNode
	 */
	public JsonNode asJson() {
		ObjectNode json = Json.newObject();
		json.put("id", getId());
		json.put("name", getName());
		json.put("kaid", getKaid());
		json.put("level", getLevel().ordinal());
		return json;
	}

	public String profileUrl() {
		return new URIBuilder().setScheme("https").setHost("www.khanacademy.org")
				.setPath(String.format("/profile/%s", getKaid())).toString();
	}

	/* GETTERS AND SETTERS */
	public String getKaid() {
		return kaid;
	}

	public void setKaid(String kaid) {
		this.kaid = kaid;
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

	public UserLevel getLevel() {
		return level;
	}

	public void setLevel(UserLevel level) {
		this.level = level;
	}

	@Override
	public String toString() {
		return "user[id = " + getId() + " | kaid = " + getKaid() + " | level = " + getLevel() + " | name = " + getName()
				+ "]";
	}
}
