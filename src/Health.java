import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.npcs.Ally;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;


import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;
import static jsclub.codefest.sdk.algorithm.PathUtils.getShortestPath;

public class Health {
    Node checkIfHasNearbyAlley(GameMap gameMap) {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Ally> allies = gameMap.getListAllies();

        for (Ally ally : allies) {
            if (distance(currentPosition, ally.getPosition()) <= 2) {
                return new Node(ally.getX(), ally.getX());
            }
        }

        return null;
    }

    void moveToAlley(GameMap gameMap, Node allyNode, Hero hero) {
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

    void healByAlly(GameMap gameMap, Hero hero) {
        Node allyNode = checkIfHasNearbyAlley(gameMap);
        if (allyNode != null) {
            System.out.println("Đã tìm thấy ally ở gần");
            moveToAlley(gameMap, allyNode, hero);
        } else {
            System.out.println("Không có ally gần để hồi máu");
        }
    }

    // Phương thức hỗ trợ để tìm vật phẩm hồi máu phù hợp nhất dựa trên lostHp
    SupportItem findBestHealingItem(List<SupportItem> supportItems, float lostHp) {
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
}
