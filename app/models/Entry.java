package models;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.Json;

import static configs.PrivateConfig.CONNECTION_STRING;
import static configs.PrivateConfig.USERNAME;
import static configs.PrivateConfig.PASSWORD;

public class Entry {
	private long programId = -1;
	private int id = -1;
	private Bracket bracket = null;
	private boolean hasBeenJudged = false;

	public Entry() {

	}

	public long getProgramId() {
		return programId;
	}

	public void setProgramId(long programId) {
		this.programId = programId;
	}

	public Bracket getBracket() {
		return bracket;
	}

	public void setBracket(Bracket bracket) {
		this.bracket = bracket;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public boolean getHasBeenJudged() {
		return hasBeenJudged;
	}

	public void setHasBeenJudged(boolean hasBeenJudged) {
		this.hasBeenJudged = hasBeenJudged;
	}

	public void realSetBracket(Bracket b) throws SQLException {
		try (Connection connection = DriverManager.getConnection(CONNECTION_STRING, USERNAME, PASSWORD)) {
			try (PreparedStatement stmt = connection.prepareStatement("UPDATE entries SET bracket_id = ? WHERE id = ? LIMIT 1")) {
				if (b == null)
					stmt.setNull(1, Types.INTEGER);
				else 
					stmt.setInt(1, b.getId());
				stmt.setInt(2, getId());
				stmt.executeUpdate();
			}
		}
		setBracket(b);
	}
	
	public JsonNode asJson() {
		ObjectNode json = Json.newObject();
		json.put("id", getId());
		json.put("programId", getProgramId());
		if (getBracket() == null)
			json.putNull("bracket");
		else
			json.replace("bracket", getBracket().asJson());
		json.put("userHasJudged", getHasBeenJudged());
		return json;
	}
}
