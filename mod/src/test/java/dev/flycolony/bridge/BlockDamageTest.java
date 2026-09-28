package dev.flycolony.bridge;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BlockDamageTest {
 @Test void instantCropsAreOneCompleteBlockNotInvalidJson(){assertEquals(1,BlockDamage.fraction(Float.POSITIVE_INFINITY));assertEquals(1,BlockDamage.fraction(5));assertEquals(.25f,BlockDamage.fraction(.25f));}
 @Test void invalidAndUnbreakableDamageCannotEscape(){assertEquals(0,BlockDamage.fraction(Float.NaN));assertEquals(0,BlockDamage.fraction(Float.NEGATIVE_INFINITY));assertEquals(0,BlockDamage.fraction(-1));}
}
