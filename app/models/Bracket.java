package models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.Json;

public class Bracket {
	private String name = null;
	private int id = -1;

	public Bracket() {

	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name.length() <= 255 ? name : name.trim().substring(0, 255);
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public JsonNode asJson() {
		ObjectNode json = Json.newObject();
		json.put("id", getId());
		json.put("name", getName());
		return json;
	}
}
