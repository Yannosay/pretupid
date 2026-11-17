package net.pretupid.client.cosmetic;

public interface CosmeticStateHolder {
	String pretupid$back();

	String pretupid$pet();

	void pretupid$setCosmetics(String back, String pet);

	float[] pretupid$capeAngles();

	void pretupid$setCapeAngles(float[] angles);

	PetFollower.Placement pretupid$petPlacement();

	void pretupid$setPetPlacement(PetFollower.Placement placement);
}
