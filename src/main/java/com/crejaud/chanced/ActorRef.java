package com.crejaud.chanced;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * An NPC or player near the local player, as plain data. Two refs are the same actor when they
 * share a kind and scene index: an NPC that transforms (the Hunllef switching styles) keeps its index.
 * Players carry no name, so a debug report holds no other player's name.
 */
@Value
class ActorRef
{
	boolean player;
	int index;
	/** NPC id when the ref was made, or -1 for players. */
	@EqualsAndHashCode.Exclude
	int npcId;
	/** NPC name, or null for players and nameless NPCs. */
	@EqualsAndHashCode.Exclude
	String name;

	static ActorRef npc(int index, int npcId, String name)
	{
		return new ActorRef(false, index, npcId, name);
	}

	static ActorRef player(int index)
	{
		return new ActorRef(true, index, -1, null);
	}
}
