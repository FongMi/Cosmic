/*
	This file is part of the OdinMS Maple Story Server
    Copyright (C) 2008 Patrick Huy <patrick.huy@frz.cc>
		       Matthias Butz <matze@odinms.de>
		       Jan Christian Meyer <vimes@odinms.de>

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as
    published by the Free Software Foundation version 3 as published by
    the Free Software Foundation. You may not use, modify or distribute
    this program under any other version of the GNU Affero General Public
    License.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package net.server.channel.handlers;

import client.Character;
import client.Client;
import net.packet.InPacket;
import server.maps.MapItem;
import server.maps.MapObject;
import server.maps.MapObjectType;
import tools.PacketCreator;
import tools.exceptions.EmptyMovementException;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MovePlayerHandler extends AbstractMovementPacketHandler {

    private static final Map<Integer, Long> lastVacTimeMap = new ConcurrentHashMap<>();
    private static final long VAC_INTERVAL_MS = 3000;

    @Override
    public void handlePacket(InPacket p, Client c) {
        p.skip(9);
        try {   // thanks Sa for noticing empty movement sequences crashing players
            int movementDataStart = p.getPosition();
            updatePosition(p, c.getPlayer(), 0);
            long movementDataLength = p.getPosition() - movementDataStart; //how many bytes were read by updatePosition
            p.seek(movementDataStart);
            c.getPlayer().getMap().movePlayer(c.getPlayer(), c.getPlayer().getPosition());
            if (c.getPlayer().isHidden()) {
                c.getPlayer().getMap().broadcastGMMessage(c.getPlayer(), PacketCreator.movePlayer(c.getPlayer().getId(), p, movementDataLength), false);
            } else {
                c.getPlayer().getMap().broadcastMessage(c.getPlayer(), PacketCreator.movePlayer(c.getPlayer().getId(), p, movementDataLength), false);
            }
            tryVac(c);
        } catch (EmptyMovementException e) {
        }
    }

    private void tryVac(Client c) {
        int playerId = c.getPlayer().getId();
        long now = System.currentTimeMillis();
        long lastVacTime = lastVacTimeMap.getOrDefault(playerId, 0L);
        if (now - lastVacTime >= VAC_INTERVAL_MS) {
            itemVac(c);
            lastVacTimeMap.put(playerId, now);
        }
    }

    private void itemVac(Client c) {
        Character player = c.getPlayer();
        List<MapObject> items = player.getMap().getMapObjectsInRange(player.getPosition(), Double.POSITIVE_INFINITY, List.of(MapObjectType.ITEM));
        for (MapObject item : items) {
            MapItem mapItem = (MapItem) item;
            if (!mapItem.canBePickedBy(player)) continue;
            if (mapItem.getOwnerId() == player.getId() || mapItem.getOwnerId() == player.getPartyId()) {
                player.pickupItem(mapItem);
            }
        }
    }
}
