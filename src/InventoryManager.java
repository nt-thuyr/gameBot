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

public class InventoryManager {

    static boolean hasPickupableItemAround(Node center, GameMap gameMap, Hero hero) {
        List<Element> items = new ArrayList<>(gameMap.getListSupportItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());
        for (Element item : items) {
            if (distance(center, item.getPosition()) <= 5) {
                if (item instanceof SupportItem supportItem) {
                    if (pickupable(hero, supportItem) != null) {
                        return true; // Có support item có thể nhặt
                    }
                }
                else {
                    if (pickupable(hero, item)) {
                        return true; // Có item có thể nhặt
                    }
                }
            }
        }
        return false;
    }

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


    void swapItem(GameMap gameMap, Hero hero) {
        Player player = gameMap.getCurrentPlayer();
        Element itemToSwap = gameMap.getElementByIndex(player.getX(), player.getY());
        if (itemToSwap instanceof SupportItem supportItem) {
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
                return;
            } else {
                // Nếu chưa đủ 4 item thì nhặt luôn
                try {
                    hero.pickupItem();
                } catch (IOException e) {
                    System.out.println("Lỗi khi nhặt support item: " + e.getMessage());
                }
                System.out.println("Đã nhặt " + supportItem.getId());
            }
        } else if (itemToSwap instanceof Weapon weapon) {
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
//                        Inventory heroWeapon = hero.getInventory();
//                        heroWeapon.setGun(weapon);
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ súng cũ, chờ lượt sau để nhặt " + weapon.getId());
                        return;
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
//                        Inventory heroWeapon = hero.getInventory();
//                        heroWeapon.setThrowable(weapon);
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        return;
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
//                        Inventory heroWeapon = hero.getInventory();
//                        heroWeapon.setMelee(weapon);
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        return;
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
//                        Inventory heroWeapon = hero.getInventory();
//                        heroWeapon.setSpecial(weapon);
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ vũ khí cũ, chờ lượt sau để nhặt " + weapon.getId());
                        return;
                    }
                }
            } else {
                System.out.println("Không nhặt vũ khí " + weapon.getId() + " vì không đủ chỉ số.");
            }
        } else if (itemToSwap instanceof Armor armor) {
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
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ giáp cũ, chờ lượt sau để nhặt " + armor.getId());
                        return;
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
                        // return luôn, không nhặt ở lượt này!
                        System.out.println("Đã bỏ giáp cũ, chờ lượt sau để nhặt " + armor.getId());
                        return;
                    }
                }
            } else {
                System.out.println("Không thể nhặt áo giáp " + armor.getId() + " vì không đủ chỉ số.");
            }
        } else {
            System.out.println("Không có item hợp lệ để nhặt.");
        }
    }

    Obstacle checkIfHasChest(GameMap gameMap, Hero hero) {
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

    void openChest(GameMap gameMap, Hero hero, Obstacle targetChest) throws IOException {
        Node targetChestNode = new Node(targetChest.getX(), targetChest.getY());
        List<Node> adjacentNodes = buildAdjacentList(targetChest);

        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

        // Nếu chưa ở vị trí vũ khí, tính đường đi
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetChestNode, true);
        System.out.println("Path tìm được: " + path);

        if (path != null && path.length() > 1) {
            String step = path.substring(0, 1);
            hero.move(step);
            System.out.println("Di chuyển: " + path);
        } else {
            if (targetChest.getHp() > 0) {
                hero.attack(Main.getDirection(currentPosition, targetChestNode));
            }
        }
    }

    void lootDroppedItems(GameMap gameMap, Hero hero, Obstacle targetChest) {
        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

        List<Node> adjacentNodes = buildAdjacentList(targetChest);

        // 1. KIỂM TRA DƯỚI CHÂN MÌNH CÓ ITEM KHÔNG (KHÔNG CHECK pickupable!)
        Element elementHere = gameMap.getElementByIndex(currentPosition.getX(), currentPosition.getY());
        if (elementHere != null) {
            // Nếu là SupportItem
            if (elementHere instanceof SupportItem) {
                List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
                if (supportItems.size() < 4) {
                    try {
                        hero.pickupItem();
                        System.out.println("Nhặt support item dưới chân.");
                    } catch (IOException e) {
                        System.out.println("Lỗi khi nhặt support item: " + e.getMessage());
                    }
                } else {
                    // Thay support item cũ yếu nhất nếu có thể
                    swapItem(gameMap, hero);
                }
                return;
            } else {
                swapItem(gameMap, hero);
            }

        }

        // 2. Nếu dưới chân không có item, tìm item pickupable gần nhất quanh rương
        Element nearestItem = null;
        Node nearestNode = null;
        int minDist = Integer.MAX_VALUE;
        for (Node node : adjacentNodes) {
            if (node.equals(currentPosition)) continue; // đã check ở trên
            Element e = gameMap.getElementByIndex(node.getX(), node.getY());
            if (e == null) continue;
            boolean canPickup = false;
            if (e instanceof SupportItem) {
                List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
                if (supportItems.size() < 4) canPickup = true;
                else if (pickupable(hero, (SupportItem) e) != null) canPickup = true;
            } else {
                canPickup = pickupable(hero, e);
            }
            if (canPickup) {
                int dist = distance(currentPosition, node);
                if (dist < minDist) {
                    minDist = dist;
                    nearestItem = e;
                    nearestNode = node;
                }
            }
        }

        if (nearestItem == null) {
            System.out.println("Không còn item pickupable quanh rương.");
            Main.lastChest = null;
            Main.lastChestPosition = null;
            return;
        }

        // 3. Di chuyển 1 bước đến item gần nhất quanh rương
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, nearestNode, true);
        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
                System.out.println("Di chuyển đến item quanh rương: " + path);
            } catch (IOException e) {
                System.out.println("Lỗi khi di chuyển: " + e.getMessage());
            }
        } else {
            System.out.println("Không tìm được đường đi đến item quanh rương!");
        }
    }

    List<Node> buildAdjacentList(Obstacle targetChest) {
        List<Node> adjacentNodes = new ArrayList<>();
        int x = targetChest.getX();
        int y = targetChest.getY();
        for (int dx = -5; dx <= 5; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                // Bỏ qua chính giữa
                // if (dx == 0 && dy == 0) continue;
                adjacentNodes.add(new Node(x + dx, y + dy));
            }
        }
        return adjacentNodes;
    }

    Obstacle getNearestChest(GameMap gameMap, Hero hero) {
        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();
        Obstacle nearestChest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if ("CHEST".equals(obstacle.getId())) {
                int dist = distance(currentPosition, obstacle.getPosition());
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestChest = obstacle;
                }
            }
        }

        return nearestChest;
    }
}