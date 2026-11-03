package com.crejaud.chanced;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where the alert sound comes from. */
@Getter
@RequiredArgsConstructor
public enum SoundType
{
	CHANCED_SOUND("Chanced sound"),
	GAME_SOUND("Game sound"),
	CUSTOM_FILE("Custom file");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
