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

    // Kiểm tra quanh vị trí center có item mà bot thực sự có thể nhặt không
    static boolean hasPickupableItemAround(Node center, GameMap gameMap, Hero hero) {
        List<Element> items = new ArrayList<>(gameMap.getListSupportItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());
        for (Element item : items) {
            if (item.getPosition() != null && distance(center, item.getPosition()) <= 5) {
                if (item instanceof SupportItem) {
                    if (hero.getInventory().getListSupportItem().size() < 4 || pickupable(hero, (SupportItem) item) != null) {
                        return true;
                    }
                } else if (pickupable(hero, item)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Kiểm tra item này có nên nhặt không (vũ khí/giáp)
    static boolean pickupable(Hero hero, Element item) {
        Inventory inventory = hero.getInventory();
        if (item instanceof Weapon weapon) {
            if (weapon.getType().equals(ElementType.MELEE)) {
                return inventory.getMelee().getId().equals("HAND") || weapon.getDamage() > inventory.getMelee().getDamage();
            } else if (weapon.getType().equals(ElementType.GUN)) {
                return inventory.getGun() == null || weapon.getDamage() > inventory.getGun().getDamage();
            } else if (weapon.getType().equals(ElementType.THROWABLE)) {
                return inventory.getThrowable() == null || weapon.getDamage() > inventory.getThrowable().getDamage();
            } else if (weapon.getType().equals(ElementType.SPECIAL)) {
                return inventory.getSpecial() == null || weapon.getDamage() > inventory.getSpecial().getDamage();
            }
        } else if (item instanceof Armor armor) {
            if (armor.getType().equals(ElementType.HELMET)) {
                if (inventory.getHelmet() == null) {
                    return true;
                } else if (armor.getDamageReduce() > inventory.getHelmet().getDamageReduce()) {
                    return true;
                } else
                    return armor.getHealthPoint() > inventory.getHelmet().getHealthPoint();
            } else if (armor.getType().equals(ElementType.ARMOR)) {
                if (inventory.getArmor() == null) {
                    return true;
                } else if (armor.getDamageReduce() > inventory.getArmor().getDamageReduce()) {
                    return true;
                } else return armor.getHealthPoint() > inventory.getArmor().getHealthPoint();
            }
        }
        return false;
    }

    // Trả về SupportItem yếu nhất trong inventory có thể thay bằng item mới (nếu có)
    static SupportItem pickupable(Hero hero, SupportItem item) {
        Inventory inventory = hero.getInventory();
        SupportItem minSupportItem = null;
        int minHealingHP = Integer.MAX_VALUE;

        if (inventory.getListSupportItem().size() >= 4) {
            for (SupportItem invSupportItem : inventory.getListSupportItem()) {
                if (invSupportItem.getHealingHP() < item.getHealingHP() && invSupportItem.getHealingHP() < minHealingHP) {
                    minSupportItem = invSupportItem;
                    minHealingHP = invSupportItem.getHealingHP();
                }
            }
        }
        return minSupportItem;
    }

    // Chỉ swap hoặc nhặt item nếu thực sự pickupable
    void swapItem(GameMap gameMap, Hero hero) {
        Player player = gameMap.getCurrentPlayer();
        Element item = gameMap.getElementByIndex(player.getX(), player.getY());
        if (item == null) return;

        if (item instanceof SupportItem supportItem) {
            List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
            SupportItem toReplace = pickupable(hero, supportItem);
            if (supportItems.size() < 4) {
                try {
                    hero.pickupItem();
                    System.out.println("Đã nhặt support item: " + supportItem.getId());
                } catch (IOException e) {
                    System.out.println("Lỗi khi nhặt support item: " + e.getMessage());
                }
            } else if (toReplace != null) {
                try {
                    hero.revokeItem(toReplace.getId());
                    System.out.println("Đã bỏ " + toReplace.getId() + ", chờ lượt sau nhặt " + supportItem.getId());
                } catch (IOException e) {
                    System.out.println("Lỗi khi bỏ support item: " + e.getMessage());
                }
            }
        } else if (item instanceof Weapon weapon && pickupable(hero, weapon)) {
            if (weapon.getType().equals(ElementType.GUN)) {
                if (hero.getInventory().getGun() == null) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt súng: " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt súng: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getGun().getId()); System.out.println("Đã bỏ súng cũ, chờ lượt sau để nhặt " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ súng: " + e.getMessage()); }
                }
            } else if (weapon.getType().equals(ElementType.THROWABLE)) {
                if (hero.getInventory().getThrowable() == null) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt vũ khí ném: " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt vũ khí ném: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getThrowable().getId()); System.out.println("Đã bỏ vũ khí ném cũ, chờ lượt sau để nhặt " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ vũ khí ném: " + e.getMessage()); }
                }
            } else if (weapon.getType().equals(ElementType.MELEE)) {
                if ("HAND".equals(hero.getInventory().getMelee().getId())) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt vũ khí cận chiến: " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt vũ khí cận chiến: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getMelee().getId()); System.out.println("Đã bỏ vũ khí cận chiến cũ, chờ lượt sau để nhặt " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ vũ khí cận chiến: " + e.getMessage()); }
                }
            } else if (weapon.getType().equals(ElementType.SPECIAL)) {
                if (hero.getInventory().getSpecial() == null) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt vũ khí đặc biệt: " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt vũ khí đặc biệt: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getSpecial().getId()); System.out.println("Đã bỏ vũ khí đặc biệt cũ, chờ lượt sau để nhặt " + weapon.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ vũ khí đặc biệt: " + e.getMessage()); }
                }
            }
        } else if (item instanceof Armor armor && pickupable(hero, armor)) {
            if (armor.getType().equals(ElementType.ARMOR)) {
                if (hero.getInventory().getArmor() == null) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt áo giáp: " + armor.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt áo giáp: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getArmor().getId()); System.out.println("Đã bỏ áo giáp cũ, chờ lượt sau để nhặt " + armor.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ áo giáp: " + e.getMessage()); }
                }
            } else if (armor.getType().equals(ElementType.HELMET)) {
                if (hero.getInventory().getHelmet() == null) {
                    try { hero.pickupItem(); System.out.println("Đã nhặt mũ: " + armor.getId()); } catch (IOException e) { System.out.println("Lỗi khi nhặt mũ: " + e.getMessage()); }
                } else {
                    try { hero.revokeItem(hero.getInventory().getHelmet().getId()); System.out.println("Đã bỏ mũ cũ, chờ lượt sau để nhặt " + armor.getId()); } catch (IOException e) { System.out.println("Lỗi khi bỏ mũ: " + e.getMessage()); }
                }
            }
        }
    }

    // Trả về true nếu nhặt/di chuyển được hoặc còn item khác pickupable quanh rương, false nếu hết sạch
    boolean lootDroppedItems(GameMap gameMap, Hero hero, Obstacle targetChest) {
        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

        List<Node> adjacentNodes = buildAdjacentList(targetChest);

        // 1. Kiểm tra dưới chân có item pickupable không
        Element elementHere = gameMap.getElementByIndex(currentPosition.getX(), currentPosition.getY());
        boolean canPickupHere = false;
        if (elementHere != null) {
            if (elementHere instanceof SupportItem) {
                List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
                if (supportItems.size() < 4) canPickupHere = true;
                else if (pickupable(hero, (SupportItem) elementHere) != null) canPickupHere = true;
            } else {
                canPickupHere = pickupable(hero, elementHere);
            }
        }
        if (canPickupHere) {
            swapItem(gameMap, hero);
            return true;
        }

        // 2. Nếu dưới chân không có item pickupable, tìm item pickupable gần nhất quanh rương
        Element nearestItem = null;
        Node nearestNode = null;
        int minDist = Integer.MAX_VALUE;
        for (Node node : adjacentNodes) {
            if (node.equals(currentPosition)) continue;
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
            return false;
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
            return true;
        } else {
            System.out.println("Không tìm được đường đi đến item quanh rương!");
            return false;
        }
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

    void openChest(GameMap gameMap, Hero hero, Obstacle targetChest) throws IOException {
        Node targetChestNode = new Node(targetChest.getX(), targetChest.getY());
        List<Node> adjacentNodes = buildAdjacentList(targetChest);

        Player player = gameMap.getCurrentPlayer();
        Node currentPosition = player.getPosition();

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

    List<Node> buildAdjacentList(Obstacle targetChest) {
        List<Node> adjacentNodes = new ArrayList<>();
        int x = targetChest.getX();
        int y = targetChest.getY();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) continue;
                adjacentNodes.add(new Node(x + dx, y + dy));
            }
        }
        return adjacentNodes;
    }
}