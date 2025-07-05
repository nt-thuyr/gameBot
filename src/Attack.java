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


    private static boolean isInsideRange(int[] range, Hero hero, Node target) {
        Player player = hero.getGameMap().getCurrentPlayer();
        if (player == null || target == null) return false;

        Node currentPosition = player.getPosition();
        String direction = Main.getDirection(player, target); // Hướng nhìn của player

        int width = range[0];
        int depth = range[1];

        int px = currentPosition.getX();
        int py = currentPosition.getY();
        int tx = target.getX();
        int ty = target.getY();

        // Dịch chuyển sang tọa độ tương đối so với player
        int dx = tx - px;
        int dy = ty - py;

        switch (direction) {
            case "UP":
                // Phía trước là giảm y
                return (dy < 0 && dy >= -depth) && (dx >= -width / 2 && dx <= width / 2);
            case "DOWN":
                // Phía trước là tăng y
                return (dy > 0 && dy <= depth) && (dx >= -width / 2 && dx <= width / 2);
            case "LEFT":
                // Phía trước là giảm x
                return (dx < 0 && dx >= -depth) && (dy >= -width / 2 && dy <= width / 2);
            case "RIGHT":
                // Phía trước là tăng x
                return (dx > 0 && dx <= depth) && (dy >= -width / 2 && dy <= width / 2);
            default:
                return false;
        }
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

    public static void attackTarget(Hero hero, Player targetPlayer, GameMap gameMap) throws IOException, InterruptedException {
        if (targetPlayer == null) {
            System.out.println("TargetNode không hợp lệ.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Node targetNode = targetPlayer.getPosition();

        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();
        Weapon melee = hero.getInventory().getMelee();
        Weapon special = hero.getInventory().getSpecial();

        // GUN
        if (gun != null && isInsideRange(gun.getRange(), hero, targetNode)) {
            String direction = Main.getDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.shoot(direction);
                System.out.println("Bắn súng về hướng " + direction);
                return;
            }
        }

        // THROWABLE
        if (throwable != null && isInsideRange(throwable.getRange(), hero, targetNode)) {
            String direction = Main.getDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.throwItem(direction);
                System.out.println("Ném vật phẩm về hướng " + direction);
                return;
            }
        }

        // SPECIAL
        if (special != null && isInsideRange(special.getRange(), hero, targetNode)) {
            String direction = Main.getDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.useSpecial(direction);
                System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
                return;
            }
        }

        // MELEE – Chỉ dùng khi sát bên
        if (melee != null && !"HAND".equals(melee.getId()) && isInsideRange(melee.getRange(), hero, targetNode)) {
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
