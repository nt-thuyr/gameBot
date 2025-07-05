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


    public static boolean isInsideRange(int[] range, Node from, Node to, String direction) {
        int width = range[0];
        int depth = range[1];
        int px = from.getX();
        int py = from.getY();
        int tx = to.getX();
        int ty = to.getY();
        int halfWidth = width / 2;

        switch (direction) {
            case "u":
                // Hướng lên: y tăng
                return (ty > py && ty <= py + depth) && (tx >= px - halfWidth && tx <= px + halfWidth);
            case "d":
                // Hướng xuống: y giảm
                return (ty < py && ty >= py - depth) && (tx >= px - halfWidth && tx <= px + halfWidth);
            case "l":
                // Hướng trái: x giảm
                return (tx < px && tx >= px - depth) && (ty >= py - halfWidth && ty <= py + halfWidth);
            case "r":
                // Hướng phải: x tăng
                return (tx > px && tx <= px + depth) && (ty >= py - halfWidth && ty <= py + halfWidth);
            default:
                return false;
        }
    }


    // Hàm tìm người chơi yếu máu nhất
    // Tìm player yếu máu nhất, nếu đều bằng nhau thì trả về null
    public static Player findWeakestPlayer(GameMap gameMap) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Player weakest = null;
        float minHealth = Float.MAX_VALUE;
        boolean allEqual = true;
        Float firstHp = null;

        for (Player p : players) {
            if (p.getPosition() == null || p.getHealth() <= 0) continue;
            if (firstHp == null) firstHp = p.getHealth();
            else if (p.getHealth() != firstHp) allEqual = false;

            if (p.getHealth() < minHealth) {
                minHealth = p.getHealth();
                weakest = p;
            }
        }
        if (allEqual) return null; // nếu tất cả cùng máu, trả về null để xử lý khác
        return weakest;
    }

    // Tìm player gần nhất
    public static Player findNearestPlayer(GameMap gameMap, Node currentPosition) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Player nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (Player p : players) {
            if (p.getPosition() == null || p.getHealth() <= 0) continue;
            int dist = distance(currentPosition, p.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }
        return nearest;
    }

    public static void attackTarget(Hero hero, Player targetPlayer, GameMap gameMap) throws IOException, InterruptedException {
        if (targetPlayer == null) {
            System.out.println("TargetNode không hợp lệ.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Node targetNode = targetPlayer.getPosition();
        String direction = Main.getDirection(currentPosition, targetNode);

        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();
        Weapon melee = hero.getInventory().getMelee();
        Weapon special = hero.getInventory().getSpecial();

        // THROWABLE
        if (throwable != null && isInsideRange(throwable.getRange(), currentPosition, targetNode, direction)) {
            hero.throwItem(direction);
            System.out.println("Ném vật phẩm về hướng " + direction);
            return;
        }

        // SPECIAL
        if (special != null && isInsideRange(special.getRange(), currentPosition, targetNode, direction)) {
            hero.useSpecial(direction);
            System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
            return;
        }

        // GUN
        if (gun != null && isInsideRange(gun.getRange(), currentPosition, targetNode, direction)) {
            hero.shoot(direction);
            System.out.println("Bắn súng về hướng " + direction);
            return;
        }

        // MELEE – Chỉ dùng khi sát bên
        if (melee != null && !"HAND".equals(melee.getId()) && isInsideRange(melee.getRange(), currentPosition, targetNode, direction)) {
            hero.attack(direction);
            System.out.println("Tấn công cận chiến vào mục tiêu ở hướng " + direction);
            return;
        }

        System.out.println("Không thể tấn công mục tiêu!");

        // Di chuyển nếu không đủ điều kiện tấn công
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        restrictedNodes.remove(targetNode);

        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, true);
        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến mục tiêu!");
            // *** THÊM DÒNG NÀY ***
            Main.lockedTarget = null; // reset để khỏi tấn công mãi vào chỗ cũ
            return;
        }

        String step = path.substring(0, 1);
        hero.move(step);
        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
    }
}