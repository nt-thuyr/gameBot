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

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;
import static jsclub.codefest.sdk.algorithm.PathUtils.getShortestPath;

public class ItemManager {

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

        if (inventory.getListSupportItem().size() >= 4) {
            for (SupportItem invSupportItem : inventory.getListSupportItem()) {
                if (item.getHealingHP() < invSupportItem.getHealingHP() && invSupportItem.getHealingHP() > minHealingHP) {
                    minSupportItem = invSupportItem;
                    minHealingHP = invSupportItem.getHealingHP();
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
                if (supportItems.size() >= 4 && existingSupportItem != null) {
                    // Nếu đã đủ 4 item, chỉ bỏ item cũ, KHÔNG nhặt luôn trong lượt này
                    try {
                        hero.revokeItem(existingSupportItem.getId());
                    } catch (IOException e) {
                        System.out.println("Lỗi khi bỏ support item: " + e.getMessage());
                    }
                    System.out.println("Đã bỏ " + existingSupportItem.getId() + ", chờ lượt sau nhặt " + supportItem.getId());
                } else {
                    // Nếu chưa đủ 4 item thì nhặt luôn
                    try {
                        hero.pickupItem();
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

    static Obstacle checkIfHasChest(GameMap gameMap) {
        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (distance(currentPosition, obstacle.getPosition()) <= 3 && "CHEST".equals(obstacle.getId())) {
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
                Main.moveToTarget(hero, targetChestNode, gameMap);
            } catch (InterruptedException e) {
                System.out.println("Lỗi khi di chuyển đến rương kho báu: " + e.getMessage());
            }
        } else {
            if (targetChest.getHp() > 0) {
                hero.attack(Main.getDirection(currentPosition, targetChestNode));
            }
        }
    }

    public static boolean lootNearbyItems(Hero hero, GameMap gameMap) {
        Node cur = gameMap.getCurrentPlayer().getPosition();
        List<Element> items = new ArrayList<>();
        items.addAll(gameMap.getListSupportItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());

        int lootRadius = 5; // bán kính 5 => vùng 10x10

        Element bestItem = null;
        Node bestPos = null;
        int minDist = Integer.MAX_VALUE;

        for (Element item : items) {
            if (item.getPosition() == null) continue;
            int dist = distance(cur, item.getPosition());
            if (dist <= lootRadius) {
                // Kiểm tra có thể nhặt
                if (ItemManager.pickupable(hero, item) ||
                        (item instanceof SupportItem && (hero.getInventory().getListSupportItem().size() < 4 || ItemManager.pickupable(hero, (SupportItem)item)!=null))) {
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
                    Main.moveToTarget(hero, bestPos, gameMap);
                } catch (IOException | InterruptedException e) {
                    System.out.println("Lỗi khi di chuyển đến item: " + e.getMessage());
                }
            }
            return true;
        }
        return false;
    }

    public static Obstacle hasEgg(GameMap gameMap) {
        for (Element element : gameMap.getListObstacles()) {
            if (element instanceof Obstacle obstacle && "EGG".equals(obstacle.getId())) {
                return obstacle; // Có ít nhất 1 quả trứng
            }
        }
        return null; // Không có quả trứng nào
    }


    public static void openEgg(GameMap gameMap, Hero hero, Obstacle targetEgg) {
        Node eggPosition = targetEgg.getPosition();
        Player currentPlayer = gameMap.getCurrentPlayer();
        Node currentPosition = currentPlayer.getPosition();
        try {
            openChest(gameMap, hero, targetEgg);
        } catch (IOException e) {
            System.out.println("Lỗi khi mở trứng: " + e.getMessage());
        }
    }

}