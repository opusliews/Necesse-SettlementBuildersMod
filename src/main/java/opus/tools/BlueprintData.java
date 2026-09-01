package opus.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public class BlueprintData {
	private final int width;
	private final int height;

	private final List<BlueprintElement> elements;
	private transient BlueprintElement[][] elementMap;

	public BlueprintData(int width, int height, List<BlueprintElement> elements) {
		this.width = width;
		this.height = height;
		this.elements = new ArrayList<>(elements);
		rebuildElementMap();
	}

	private void rebuildElementMap() {
		elementMap = new BlueprintElement[width][height];

		for (BlueprintElement element : elements) {
			int x = element.getX();
			int y = element.getY();

			if (x >= 0 && y >= 0 && x < width && y < height) {
				elementMap[x][y] = element;
			}
		}
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public List<BlueprintElement> getElements() {
		return new ArrayList<>(elements);
	}

	public BlueprintData rotateClockwise() {
		List<BlueprintElement> rotatedElements = new ArrayList<>();

		for (BlueprintElement element : elements) {
			int newX = height - 1 - element.getY();
			int newY = element.getX();

			BlueprintElement rotatedElement = new BlueprintElement(
				newX,
				newY
			);

			if (element.getTileID() != null) {
				rotatedElement.setTileID(element.getTileID());
			}

			if (element.getObjectID() != null) {
				rotatedElement.setObjectID(element.getObjectID());

				rotatedElement.setRotation(
					(element.getRotation() + 1) % 4
				);
			}

			rotatedElements.add(rotatedElement);
		}

		// Rotation switches Width and Height
		return new BlueprintData(height,width,rotatedElements);
	}

	public BlueprintData rotateCounterClockwise() {
		List<BlueprintElement> rotatedElements = new ArrayList<>();

		for (BlueprintElement element : elements) {
			int newX = element.getY();
			int newY = width - 1 - element.getX();

			BlueprintElement rotatedElement = new BlueprintElement(
				newX,
				newY
			);

			if (element.getTileID() != null) {
				rotatedElement.setTileID(element.getTileID());
			}

			if (element.getObjectID() != null) {
				rotatedElement.setObjectID(element.getObjectID());

				rotatedElement.setRotation(
					(element.getRotation() + 3) % 4
				);
			}

			rotatedElements.add(rotatedElement);
		}

		// Rotation switches Width and Height
		return new BlueprintData(height,width,rotatedElements);
	}

	public BlueprintElement getElementAt(int x, int y) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return null;
		}

		return elementMap[x][y];
	}

	public String toJson() {
		JsonObject root = new JsonObject();

		root.addProperty("width", width);
		root.addProperty("height", height);

		JsonArray elementsJson = new JsonArray();

		for (BlueprintElement element : elements) {
			JsonObject elementJson = new JsonObject();

			elementJson.addProperty("x", element.getX());
			elementJson.addProperty("y", element.getY());

			if (element.getTileID() != null) {
				elementJson.addProperty("tileID", element.getTileID());
			}

			if (element.getObjectID() != null) {
				elementJson.addProperty("objectID", element.getObjectID());
				elementJson.addProperty("rotation", element.getRotation());
			}

			elementsJson.add(elementJson);
		}

		root.add("elements", elementsJson);

		return root.toString();
	}

	public static BlueprintData fromJson(String json) {
		JsonObject root = JsonParser
				.parseString(json)
				.getAsJsonObject();

		int width = root.get("width").getAsInt();
		int height = root.get("height").getAsInt();

		List<BlueprintElement> elements = new ArrayList<>();

		JsonArray elementsJson = root.getAsJsonArray("elements");

		for (JsonElement jsonElement : elementsJson) {
			JsonObject elementJson = jsonElement.getAsJsonObject();

			int x = elementJson.get("x").getAsInt();
			int y = elementJson.get("y").getAsInt();

			BlueprintElement element = new BlueprintElement(x, y);

			if (elementJson.has("tileID")) {
				element.setTileID(
						elementJson.get("tileID").getAsString()
				);
			}

			if (elementJson.has("objectID")) {
				element.setObjectID(
						elementJson.get("objectID").getAsString()
				);

				if (elementJson.has("rotation")) {
					element.setRotation(
							elementJson.get("rotation").getAsInt()
					);
				}
			}

			elements.add(element);
		}

		return new BlueprintData(
				width,
				height,
				elements
		);
	}
}