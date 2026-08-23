package net.woistjojo.borderBattle.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerData {
    private UUID uuid;
    private String name;
    private boolean moderator;
    private boolean eliminated;
    private int totalPlayers;
    private int placement;
}
