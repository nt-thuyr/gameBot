import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;
import static jsclub.codefest.sdk.algorithm.PathUtils.getShortestPath;

public class Attack {

    public static boolean isStraightLine(Node from, Node to) {
        return from.getX() == to.getX() || from.getY() == to.getY();
    }

    private static int rangeCalculator(int[] x) {
        int range = 0;
        for (int i : x) {
            range += i * i;
        }
        return (int) Math.sqrt(range);
    }


    // Hàm tìm người chơi yếu máu nhất
    public static Player findWeakestPlayer(GameMap gameMap) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Player weakest = null;
        float minHealth = Float.MAX_VALUE;

        for (Player p : players) {
            if (p.getPosition() == null || p.getHealth() <= 0) continue;
            if (p.getHealth() < minHealth) {
                minHealth = p.getHealth();
                weakest = p;
            }
        }
        return weakest;
    }

    public static void attackTarget(Hero hero, Node targetNode, GameMap gameMap) throws IOException, InterruptedException {
        if (targetNode == null) {
            System.out.println("TargetNode không hợp lệ.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        int dist = distance(currentPosition, targetNode);

        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();
        Weapon melee = hero.getInventory().getMelee();
        Weapon special = hero.getInventory().getSpecial();

        // GUN
        if (gun != null && dist <= rangeCalculator(gun.getRange()) && isStraightLine(currentPosition, targetNode)) {
            String direction = Main.getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.shoot(direction);
                System.out.println("Bắn súng về hướng " + direction);
                return;
            }
        }

        // THROWABLE
        if (throwable != null && dist <= rangeCalculator(throwable.getRange()) && isStraightLine(currentPosition, targetNode)) {
            String direction = Main.getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.throwItem(direction);
                System.out.println("Ném vật phẩm về hướng " + direction);
                return;
            }
        }

        // SPECIAL
        if (special != null && dist <= rangeCalculator(special.getRange()) && isStraightLine(currentPosition, targetNode)) {
            String direction = Main.getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.useSpecial(direction);
                System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
                return;
            }
        }

        // MELEE – Chỉ dùng khi sát bên
        if (melee != null && !"HAND".equals(melee.getId()) && dist == 1) {
            String direction = Main.getDirection(currentPosition, targetNode);
            hero.attack(direction);
            System.out.println("Tấn công cận chiến vào mục tiêu ở hướng " + direction);
            return;
        }

        System.out.println("Không thể tấn công mục tiêu!");

        // Di chuyển nếu không đủ điều kiện tấn công
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến mục tiêu!");
            return;
        }

        String step = path.substring(0, 1);
        hero.move(step);
        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
    }

}
