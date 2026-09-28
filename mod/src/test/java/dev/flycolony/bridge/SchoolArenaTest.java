package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SchoolArenaTest {
 @Test void schoolCannotResetChallengeOrOrdinaryWorlds(){
  assertThrows(IllegalArgumentException.class,()->SchoolArena.validate("Fly Colony Challenge","hold_attack",1));
  assertThrows(IllegalArgumentException.class,()->SchoolArena.validate("My World","hold_attack",1));
  assertDoesNotThrow(()->SchoolArena.validate("Fly Colony School 2026","hold_attack",1));
 }
 @Test void schoolRejectsUnknownLessonsAndUnboundedSeeds(){
  assertThrows(IllegalArgumentException.class,()->SchoolArena.validate("Fly Colony School","give_diamonds",1));
  assertThrows(IllegalArgumentException.class,()->SchoolArena.validate("Fly Colony School","hold_attack",-1));
 }
}
