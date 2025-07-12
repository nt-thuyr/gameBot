import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;


import java.io.IOException;
import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;
import static jsclub.codefest.sdk.algorithm.PathUtils.getShortestPath;

public class Health {

    private static long lastUsedCompass = 0; // Biến để lưu thời gian sử dụng la bàn

    static Node checkIfHasNearbyAlly(GameMap gameMap, int radius) {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Ally> allies = gameMap.getListAllies();

        for (Ally ally : allies) {
            if (distance(currentPosition, ally.getPosition()) <= radius && ally.getCooldownStepLeft() <= 1) {
                return new Node(ally.getX(), ally.getX());
            }
        }

        return null;
    }

    static Node findNearestAlly(GameMap gameMap) {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Ally> allies = gameMap.getListAllies();
        Node nearestAlly = null;
        float minDistance = Float.MAX_VALUE;

        for (Ally ally : allies) {
            float dist = distance(currentPosition, ally.getPosition());
            if (dist < minDistance) {
                minDistance = dist;
                nearestAlly = ally.getPosition();
            }
        }

        return nearestAlly;
    }

    static void moveToAlly(GameMap gameMap, Node allyNode, Hero hero) {
        Player currentPlayer = gameMap.getCurrentPlayer();

        // Nếu chưa ở vị trí vũ khí, tính đường đi
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPlayer.getPosition(), allyNode, true);
        System.out.println("Path tìm được: " + path);

        if (path != null && path.length() > 1) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
            } catch (Exception e) {
                System.out.println("Lỗi khi di chuyển: " + e.getMessage());
                return;
            }
            System.out.println("Di chuyển: " + path);
        } else {
            System.out.println("Đã di chuyển đến ally gần nhất");
        }
    }

    static boolean healByAlly(GameMap gameMap, Hero hero) {
        Node allyNode = checkIfHasNearbyAlly(gameMap, 8);
        if (allyNode != null) {
            System.out.println("Đã tìm thấy ally ở gần");
            moveToAlly(gameMap, allyNode, hero);
        } else {
            System.out.println("Không có ally gần để hồi máu");
            return false;
        }
        return true;
    }

    // Phương thức hỗ trợ để tìm vật phẩm hồi máu phù hợp nhất dựa trên lostHp
    static SupportItem findBestHealingItem(List<SupportItem> supportItems, float lostHp) {
        if (supportItems == null || supportItems.isEmpty()) {
            return null; // Không có vật phẩm hồi máu
        }
        SupportItem bestItem = null;
        float bestHealingHp = Float.MAX_VALUE;
        // Chọn item healing >= lostHp nhỏ nhất, nếu không có thì healing lớn nhất nhỏ hơn lostHp
        for (SupportItem item : supportItems) {
            float heal = item.getHealingHP();
            if (heal >= lostHp && heal < bestHealingHp) {
                bestItem = item;
                bestHealingHp = heal;
            }
        }
        if (bestItem == null) {
            bestHealingHp = 0;
            for (SupportItem item : supportItems) {
                float heal = item.getHealingHP();
                if (heal > bestHealingHp && heal < lostHp) {
                    bestItem = item;
                    bestHealingHp = heal;
                }
            }
        }
        return bestItem;
    }

    static void useSpecialSupportItem(GameMap gameMap, Hero hero) {
        List<SupportItem> supportItemInv = hero.getInventory().getListSupportItem();
        Player currentPlayer = gameMap.getCurrentPlayer();
        float currentHealth = currentPlayer.getHealth();

        // Nếu có Elixir thì dùng luôn
        // Hiệu ứng: miễn khống chế
        if (supportItemInv.stream().anyMatch(item -> item.getId().equals("ELIXIR"))) {
            try {
                hero.useItem("ELIXIR");
            } catch (IOException e) {
                System.out.println("Lỗi khi sử dụng ELIXIR: " + e.getMessage());
            }
        }

        // Nếu có Magic, không có locked target hoặc locked target khỏe hơn
        // Sau khi sử dụng thì locked target yếu nhất
        // Hiệu ứng: tàng hình
        if (supportItemInv.stream().anyMatch(item -> item.getId().equals("MAGIC")) &&
                (Main.lockedTarget == null || Main.lockedTarget.getHealth() > currentHealth)) {
            try {
                hero.useItem("MAGIC");
                Main.lockedTarget = Attack.findWeakestPlayer(gameMap);
            } catch (IOException e) {
                System.out.println("Lỗi khi sử dụng MAGIC: " + e.getMessage());
            }
        }

        // Nếu có Compass, khu vực hiện tại có ít nhất 2 người chơi, không có locked target hoặc locked target khỏe hơn mình, lần cuối sử dụng là 10 step trước
        // Sau khi sử dụng thì target đến player gần nhất
        // Hiệu ứng: làm choáng player trong 9*9
        if (supportItemInv.stream().anyMatch(item -> item.getId().equals("COMPASS"))) {
            if (MapManager.isCurrentAreaCrowded(gameMap, 2) && (Main.lockedTarget == null || Main.lockedTarget.getHealth() > currentHealth)) {
                if (gameMap.getStepNumber() > lastUsedCompass + 10) {
                    try {
                        hero.useItem("COMPASS");
                        lastUsedCompass = gameMap.getStepNumber();
                        Main.lockedTarget = Attack.findNearestPlayer(gameMap, currentPlayer.getPosition());
                    } catch (IOException e) {
                        System.out.println("Lỗi khi sử dụng COMPASS: " + e.getMessage());
                    }
                } else {
                    System.out.println("Đã sử dụng COMPASS gần đây, không sử dụng lại ngay.");
                }
            }
        }
    }
}
