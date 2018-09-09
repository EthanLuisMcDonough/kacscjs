package models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

public class Criterion {
    private String name = null, description = null;
    private int weight = -1, id = -1;

    public Criterion() {

    }

    /**
     * Returns a JsonNode representing the Criterion
     *
     * @return A JsonNode representing the Criterion
     */
    public JsonNode asJson() {
        ObjectNode json = Json.newObject();
        json.put("id", getId());
        json.put("name", getName());
        json.put("description", getDescription());
        json.put("weight", getWeight());
        return json;
    }

    /* GETTERS AND SETTERS */
    public int getWeight() {
        return weight;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getId() {
        return id;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public void setName(String name) {
        this.name = name.length() <= 255 ? name : name.substring(0, 255);
    }

    public void setDescription(String description) {
        this.description = description.length() <= 500 ? description : description.substring(0, 500);
    }

    public void setId(int id) {
        this.id = id;
    }
}
