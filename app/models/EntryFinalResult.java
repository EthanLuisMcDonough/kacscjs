package models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import play.libs.Json;

public class EntryFinalResult {
	private int entryId = -1;
	private double result = -1;
	private long programId = -1;

	public EntryFinalResult() {

	}

	public int getEntryId() {
		return entryId;
	}

	public void setEntryId(int entryId) {
		this.entryId = entryId;
	}

	public double getResult() {
		return result;
	}

	public void setResult(double result) {
		this.result = result;
	}

	public long getProgramId() {
		return programId;
	}

	public void setProgramId(long programId) {
		this.programId = programId;
	}

	public JsonNode asJson() {
		ObjectNode json = Json.newObject();
		json.put("entryId", getEntryId());
		json.put("result", getResult());
		json.put("programId", getProgramId());
		return json;
	}
}
