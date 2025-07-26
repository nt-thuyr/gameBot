import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.*;

public class ItemManager {

    static int compassNum = 0; // Đếm số la bàn trong inventory
    static final int MAX_COMPASS = 2; // Giới hạn số la bàn có thể mang
    static final int MAX_SUPPORT_ITEMS = 4; // Giới hạn số support item có thể mang

    // Kiểm tra chỉ số của item xem có nên nhặt không
    static boolean pickupable(Hero hero, Element item) {
        Inventory inventory = hero.getInventory();
        if (item instanceof Weapon weapon) {
            if (weapon.getType().equals(ElementType.MELEE)) {
                return inventory.getMelee().getId().equals("HAND") || weapon.getDamage() > inventory.getMelee().getDamage(); // Nhặt vũ khí MELEE
            } else if (weapon.getType().equals(ElementType.GUN)) {
                return inventory.getGun() == null || weapon.getDamage() > inventory.getGun().getDamage(); // Nhặt vũ khí GUN
            } else if (weapon.getType().equals(ElementType.THROWABLE)) {
                return inventory.getThrowable() == null || weapon.getDamage() > inventory.getThrowable().getDamage(); // Nhặt vũ khí THROWABLE
            } else if (weapon.getType().equals(ElementType.SPECIAL)) {
                return inventory.getSpecial() == null || weapon.getDamage() > inventory.getSpecial().getDamage(); // Nhặt vũ khí SPECIAL
            }
        } else if (item instanceof Armor armor) {
            if (armor.getType().equals(ElementType.HELMET)) {
                if (inventory.getHelmet() == null) {
                    return true; // Nhặt mũ
                } else if (armor.getDamageReduce() > inventory.getHelmet().getDamageReduce()) {
                    return true; // Nhặt mũ nếu mạnh hơn
                } else
                    return armor.getHealthPoint() > inventory.getHelmet().getHealthPoint(); // Nhặt mũ nếu HP cao hơn
            } else if (armor.getType().equals(ElementType.ARMOR)) {
                if (inventory.getArmor() == null) {
                    return true; // Nhặt mũ
                } else if (armor.getDamageReduce() > inventory.getArmor().getDamageReduce()) {
                    return true; // Nhặt mũ nếu mạnh hơn
                } else return armor.getHealthPoint() > inventory.getArmor().getHealthPoint(); // Nhặt mũ nếu HP cao hơn
            }
        }
        return false;
    }

    static SupportItem pickupable(Hero hero, SupportItem item) {
        Inventory inventory = hero.getInventory();

        int minHealingHP = 0;
        SupportItem minSupportItem = null;

        if (inventory.getListSupportItem().size() >= MAX_SUPPORT_ITEMS) {
            for (SupportItem invSupportItem : inventory.getListSupportItem()) {
                if ("COMPASS".equals(invSupportItem.getId())) {
                    if (compassNum <= MAX_COMPASS) {
                        continue; // chỉ cần tối đa MAX_COMPASS (2) trong inventory
                    }
                } else if ("MAGIC".equals(invSupportItem.getId())) {
                    continue;
                }
                if (item.getHealingHP() > invSupportItem.getHealingHP() || item.getId().equals("MAGIC") || (item.getId().equals("COMPASS") && compassNum < MAX_COMPASS)) {
                    if (invSupportItem.getHealingHP() < minHealingHP) {
                        minSupportItem = invSupportItem;
                        minHealingHP = invSupportItem.getHealingHP();
                    }
                }
            }
        }
        return minSupportItem;
    }


    static void swapItem(GameMap gameMap, Hero hero) {
        Player player = gameMap.getCurrentPlayer();
        Element itemToSwap = gameMap.getElementByIndex(player.getX(), player.getY());
        switch (itemToSwap) {
            case SupportItem supportItem -> {
                SupportItem existingSupportItem = pickupable(hero, supportItem);
                List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
                if (supportItems.size() >= MAX_SUPPORT_ITEMS && existingSupportItem != null) {
                    // Nếu đã đủ 4 item, chỉ bỏ item cũ, KHÔNG nhặt luôn trong lượt này
                    try {
                        hero.useItem(existingSupportItem.getId());
                        if ("COMPASS".equals(existingSupportItem.getId())) {
                            compassNum--;
                        }
                    } catch (IOException e) {
                        System.out.println("Lỗi khi bỏ support item: " + e.getMessage());
                    }
                    System.out.println("Đã dùng " + existingSupportItem.getId() + ", chờ lượt sau nhặt " + supportItem.getId());
                } else {
                    // Nếu chưa đủ 4 item thì nhặt luôn
                    try {
                        hero.pickupItem();
                        if ("COMPASS".equals(supportItem.getId())) {
                            compassNum++;
                        }
                    } catch (IOException e) {
                        System.out.println("Lỗi khi nhặt support item: " + e.getMessage());
                    }
                    System.out.println("Đã nhặt " + supportItem.getId());
                }
            }
            case Weapon weapon -> {
                if (pickupable(hero, weapon)) {
                    // Nếu là súng
                    if (weapon.getType().equals(ElementType.GUN)) {
                        if (hero.getInventory().getGun() == null) { // Nếu chưa có súng
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt súng: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getGun().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ súng: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ súng cũ, chờ lượt sau để nhặt " + weapon.getId());
                        }
                    }
                    // Nếu là vũ khí ném
                    else if (weapon.getType().equals(ElementType.THROWABLE)) {
                        if (hero.getInventory().getThrowable() == null) { // Nếu chưa có vũ khí ném
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt súng: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getThrowable().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ vũ khí ném: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        }
                    }
                    // Nếu là vũ khí cận chiến
                    else if (weapon.getType().equals(ElementType.MELEE)) {
                        if ("HAND".equals(hero.getInventory().getMelee().getId())) { // Nếu chưa có cận chiến
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt vũ khí cận chiến: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getMelee().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ vũ khí cận chiến: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        }
                    }
                    // Nếu là vũ khí đặc biệt
                    else if (weapon.getType().equals(ElementType.SPECIAL)) {
                        if (hero.getInventory().getSpecial() == null) { // Nếu chưa có vũ khí đă biệt
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt vũ khí đặc biệt: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getSpecial().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ vũ khí đặc biệt: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        }
                    }
                } else {
                    System.out.println("Không nhặt vũ khí " + weapon.getId() + " vì không đủ chỉ số.");
                }
            }
            case Armor armor -> {
                if (pickupable(hero, armor)) {
                    if (armor.getType().equals(ElementType.ARMOR)) {
                        if (hero.getInventory().getArmor() == null) { // Nếu chưa có áo giáp
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt áo giáp: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getArmor().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ áo giáp: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ giáp cũ, chờ lượt sau để nhặt " + armor.getId());
                        }
                    } else if (armor.getType().equals(ElementType.HELMET)) {
                        if (hero.getInventory().getHelmet() == null) { // Nếu chưa có mũ
                            try {
                                hero.pickupItem();
                            } catch (IOException e) {
                                System.out.println("Lỗi khi nhặt mũ: " + e.getMessage());
                            }
                        } else {
                            try {
                                hero.revokeItem(hero.getInventory().getHelmet().getId());
                            } catch (IOException e) {
                                System.out.println("Lỗi khi bỏ mũ: " + e.getMessage());
                            }
                            System.out.println("Đã bỏ giáp cũ, chờ lượt sau để nhặt " + armor.getId());
                        }
                    }
                } else {
                    System.out.println("Không thể nhặt áo giáp " + armor.getId() + " vì không đủ chỉ số.");
                }
            }
            case null, default -> System.out.println("Không có item hợp lệ để nhặt.");
        }
    }

    static Obstacle checkIfHasChest(GameMap gameMap, int range) {
        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (!checkInsideSafeArea(obstacle, safeZone, mapSize)) continue;
            if (distance(currentPosition, obstacle.getPosition()) <= range && "CHEST".equals(obstacle.getId())) {
                System.out.println("Có rương kho báu gần đây, hãy mở nó!");
                return obstacle;
            }
        }
        return null;
    }

    static void openChest(GameMap gameMap, Hero hero, Obstacle targetChest) throws IOException {
        Node targetChestNode = new Node(targetChest.getX(), targetChest.getY());

        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

        if (distance(currentPosition, targetChestNode) > 1) {
            try {
                Main.moveToTarget(hero, targetChestNode, gameMap, true);
            } catch (InterruptedException e) {
                System.out.println("Lỗi khi di chuyển đến rương kho báu: " + e.getMessage());
            }
        } else {
            if (targetChest.getHp() > 0) {
                hero.attack(Main.getDirection(currentPosition, targetChestNode));
            }
        }
    }

    public static boolean lootNearbyItems(Hero hero, GameMap gameMap, int lootRadius) {
        Node cur = gameMap.getCurrentPlayer().getPosition();
        List<Element> items = new ArrayList<>();
        items.addAll(gameMap.getListSupportItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());

        Element bestItem = null;
        Node bestPos = null;
        int minDist = Integer.MAX_VALUE;

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Element item : items) {
            if (!checkInsideSafeArea(item, safeZone, mapSize)) continue;
            if (item.getPosition() == null) continue;
            int dist = distance(cur, item.getPosition());
            if (dist <= lootRadius) {
                // Kiểm tra có thể nhặt
                if (ItemManager.pickupable(hero, item) ||
                        (item instanceof SupportItem && (hero.getInventory().getListSupportItem().size() < MAX_SUPPORT_ITEMS || ItemManager.pickupable(hero, (SupportItem)item)!=null))) {
                    // Ưu tiên item gần nhất trong vùng
                    if (dist < minDist) {
                        minDist = dist;
                        bestItem = item;
                        bestPos = item.getPosition();
                    }
                }
            }
        }

        if (bestItem != null) {
            // Nếu đang đứng trên item thì nhặt luôn
            if (minDist == 0) {
                swapItem(gameMap, hero);
                System.out.println("Đã nhặt/lấy item tại vị trí hiện tại: " + bestItem.getId());
            } else {
                try {
                    Main.moveToTarget(hero, bestPos, gameMap, true);
                } catch (IOException | InterruptedException e) {
                    System.out.println("Lỗi khi di chuyển đến item: " + e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    public static boolean pickUpNearestWeapon(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        List<Weapon> weapons = gameMap.getListWeapons();
        if (weapons == null || weapons.isEmpty()) {
            System.out.println("Không tìm thấy vũ khí trên bản đồ!");
            return false;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();

        Weapon nearestWeapon = null;
        int minDistance = Integer.MAX_VALUE;
        Node nearestNode = null;

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Weapon weapon : weapons) {
            if (ItemManager.pickupable(hero, weapon)) {
                Node weaponNode = weapon.getPosition();
                if (!checkInsideSafeArea(weaponNode, safeZone, mapSize)) continue;
                int dist = distance(currentPosition, weaponNode);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestWeapon = weapon;
                    nearestNode = weaponNode;
                }
            }
        }

        if (nearestWeapon == null) {
            System.out.println("Không tìm thấy vũ khí hợp lệ trong vùng an toàn!");
            return false;
        }

        if (distance(currentPosition, nearestNode) > 0) {
            // Nếu không đứng trên vũ khí, di chuyển đến vị trí vũ khí
            System.out.println("Đang di chuyển đến vũ khí gần nhất: " + nearestWeapon.getId() + " tại " + nearestNode);
            Main.moveToTarget(hero, nearestNode, gameMap, true);
        } else {
            // Nếu đã đứng trên vị trí vũ khí thì nhặt luôn, không di chuyển
            ItemManager.swapItem(gameMap, hero);
            System.out.println("Đang đứng trên vũ khí, thực hiện nhặt.");
        }

        return true;
    }

    public static Obstacle hasEgg(GameMap gameMap, int range) {
        List<Obstacle> obstacles = gameMap.getListObstacles();
        for (Obstacle chest : obstacles) {
            if ("DRAGON_EGG".equals(chest.getId()) &&
                    distance(gameMap.getCurrentPlayer().getPosition(), chest.getPosition()) <= range) {
                return chest; // Có ít nhất 1 quả trứng
            }
        }
        return null; // Không có quả trứng nào
    }
}