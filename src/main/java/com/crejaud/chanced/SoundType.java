package com.crejaud.chanced;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Where the alert sound comes from. */
@Getter
@RequiredArgsConstructor
enum SoundType
{
	GAME_SOUND("Game sound"),
	CUSTOM_FILE("Custom file");

	private final String name;

	@Override
	public String toString()
	{
		return name;
	}
}
