package com.trackscapeconnector;

import net.runelite.api.Client;
import net.runelite.api.clan.ClanChannel;
import net.runelite.api.clan.ClanChannelMember;
import net.runelite.client.game.WorldService;
import net.runelite.client.util.Text;
import net.runelite.http.api.worlds.World;
import net.runelite.http.api.worlds.WorldType;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Finding world type the player is on is based off of League Chat Broadcasts Plugin.
// https://github.com/Memebeams/league-chat-broadcasts/
public class FindWordTypeService {
    public final Set<String> CLAN_MEMBER_NAMES = new HashSet<>();
    private Set<Integer> LEAGUE_WORLDS = new HashSet<>();
    private WorldService worldService;
    private Client client;

    public FindWordTypeService(WorldService worldService, Client client) {
        this.worldService = worldService;
        this.client = client;
    }

    public void loadWorldTypes() {
        LEAGUE_WORLDS = worldService.getWorlds().getWorlds().stream()
                .filter(world -> world.getTypes().contains(WorldType.SEASONAL))
                .map(World::getId)
                .collect(Collectors.toSet());
    }

    public void loadClanMembers() {
        if (client.getClanChannel() == null) {
            return;
        }

        var clanMates = client.getClanChannel().getMembers().stream()
                .map(ClanChannelMember::getName)
                .map(Text::toJagexName)
                .collect(Collectors.toSet());
        CLAN_MEMBER_NAMES.addAll(clanMates);
    }

    public boolean isPlayerInLeaguesWorld(String message) {

        String cleanMessage = Text.sanitize(message);
        Optional<String> possibleClanmateName = CLAN_MEMBER_NAMES.stream().filter(cleanMessage::startsWith).findAny();

        if (possibleClanmateName.isEmpty()) return false;
        String clanmateName = possibleClanmateName.get();

        ClanChannel clan = client.getClanChannel();
        assert clan != null;
        var possibleClanMember = clan.findMember(clanmateName);

        assert possibleClanMember != null;
        return LEAGUE_WORLDS.contains(possibleClanMember.getWorld());
    }
}
