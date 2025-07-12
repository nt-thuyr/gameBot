import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.util.ArrayList;
import java.util.List;

public class InventoryManager {
    static boolean checkIfHasWeapon(Hero hero) {
        if (hero.getInventory().getGun() != null || hero.getInventory().getThrowable() != null ||
                (hero.getInventory().getSpecial() != null && "SAHUR_BAT".equals(hero.getInventory().getSpecial())) ||
                (!"HAND".equals(hero.getInventory().getMelee())))
        {
            return true;
        }
        return false;
    }
}
