package com.society.sunlitmarket.core;

import com.society.sunlitmarket.compat.OwnerKey;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class OwnerKeyTest {
    @Test void oldUuidRowsBecomePersonal(){UUID id=UUID.randomUUID();assertEquals(OwnerKey.player(id),OwnerKey.parse(id.toString()));}
    @Test void playerAndTeamCannotCollide(){UUID id=UUID.randomUUID();assertNotEquals(new OwnerKey(true,id).key(),OwnerKey.player(id).key());}
    @Test void teamIdentityRoundTrips(){OwnerKey key=new OwnerKey(true,UUID.randomUUID());assertEquals(key,OwnerKey.parse(key.key()));}
    @Test void malformedKeysAreRejected(){assertThrows(IllegalArgumentException.class,()->OwnerKey.parse("team:invalid"));}
}
