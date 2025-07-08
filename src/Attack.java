import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;
import static jsclub.codefest.sdk.algorithm.PathUtils.getShortestPath;

public class Attack {

    private static long lastShotTime = 0; // Lưu thời gian bắn súng cuối
    private static long lastThrowTime = 0; // Lưu thời gian ném vật phẩm cuối
    private static long lastSpecialTime = 0; // Lưu thời gian dùng vũ khí đặc biệt cuối
    private static long lastMeleeTime = 0; // Lưu thời gian tấn công cận chiến cuối
    private static final long STEP_TIME = 500; // Thời gian 1 step game là 500ms

    public static boolean isInsideRange(int[] range, Node from, Node to, String direction) {
        int width = range[0];
        int depth = range[1];
        int px = from.getX();
        int py = from.getY();
        int tx = to.getX();
        int ty = to.getY();
        int halfWidth = width / 2;

        return switch (direction) {
            case "u" ->
                // Hướng lên: y tăng
                    (ty > py && ty <= py + depth) && (tx >= px - halfWidth && tx <= px + halfWidth);
            case "d" ->
                // Hướng xuống: y giảm
                    (ty < py && ty >= py - depth) && (tx >= px - halfWidth && tx <= px + halfWidth);
            case "l" ->
                // Hướng trái: x giảm
                    (tx < px && tx >= px - depth) && (ty >= py - halfWidth && ty <= py + halfWidth);
            case "r" ->
                // Hướng phải: x tăng
                    (tx > px && tx <= px + depth) && (ty >= py - halfWidth && ty <= py + halfWidth);
            default -> false;
        };
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
            else if (!Objects.equals(p.getHealth(), firstHp)) allEqual = false;

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

//    public static void attackTarget(Hero hero, Player targetPlayer, GameMap gameMap) throws IOException, InterruptedException {
//        if (targetPlayer == null) {
//            System.out.println("TargetNode không hợp lệ.");
//            return;
//        }
//
//        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
//        Node targetNode = targetPlayer.getPosition();
//
//        if (attackAlternately(gameMap, hero, targetNode)) return; // Đã tấn công được, không cần di chuyển
//
//        System.out.println("Không thể tấn công mục tiêu!");
//
//        // Di chuyển nếu không đủ điều kiện tấn công
//        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
//        restrictedNodes.remove(targetNode);
//
//        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, true);
//        if (path == null || path.isEmpty()) {
//            System.out.println("Không tìm thấy đường đi đến mục tiêu!");
//            // *** THÊM DÒNG NÀY ***
//            Main.lockedTarget = null; // reset để khỏi tấn công mãi vào chỗ cũ
//            return;
//        }
//
//        String step = path.substring(0, 1);
//        hero.move(step);
//        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
//    }
//
//    // attackAlternately để tấn công liên tục trong 1 lượt
//    public static boolean attackAlternately(GameMap gameMap, Hero hero, Node targetNode) throws IOException {
//        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
//        String direction = Main.getDirection(currentPosition, targetNode);
//
//        Weapon gun = hero.getInventory().getGun();
//        Weapon throwable = hero.getInventory().getThrowable();
//        Weapon melee = hero.getInventory().getMelee();
//        Weapon special = hero.getInventory().getSpecial();
//
//        // Bắn/ném/dùng special nếu trong tầm
//        if (gun != null && isInsideRange(gun.getRange(), currentPosition, targetNode, direction)) {
//            hero.shoot(direction);
//            System.out.println("Bắn súng về hướng " + direction);
//        } else return false;
//
//        if (throwable != null && isInsideRange(throwable.getRange(), currentPosition, targetNode, direction)) {
//            hero.throwItem(direction);
//            System.out.println("Ném vật phẩm về hướng " + direction);
//        } else return false;
//
//        if (special != null && isInsideRange(special.getRange(), currentPosition, targetNode, direction)) {
//            hero.useSpecial(direction);
//            System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
//        } else return false;
//
//        // Nếu đủ tầm melee thì tấn công melee và dừng (không di chuyển nữa)
//        if (melee != null && !"HAND".equals(melee.getId()) && isInsideRange(melee.getRange(), currentPosition, targetNode, direction)) {
//            hero.attack(direction);
//            System.out.println("Tấn công cận chiến vào mục tiêu ở hướng " + direction);
//        } else return false;
//
//        return true;
//    }

    // Thử tấn công mục tiêu bằng các vũ khí có thể
    public static boolean attackTarget(Hero hero, Player targetPlayer, GameMap gameMap) throws IOException {
        if (targetPlayer == null) {
            System.out.println("Target Player không hợp lệ.");
            return false;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Node targetNode = targetPlayer.getPosition();
        String direction = Main.getDirection(currentPosition, targetNode);

        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();
        Weapon melee = hero.getInventory().getMelee();
        Weapon special = hero.getInventory().getSpecial();

        boolean attacked = false;

        // Bắn súng nếu trong tầm và hết cooldown
        if (gun != null && isInsideRange(gun.getRange(), currentPosition, targetNode, direction)
                && System.currentTimeMillis() - lastShotTime >= gun.getCooldown() * STEP_TIME) {
            hero.shoot(direction); // Bắn lần 1
            System.out.println("Bắn súng lần 1 về hướng " + direction);
            // Thử bắn lần 2 để kiểm tra trick bỏ qua cooldown
            try {
                hero.shoot(direction);
                System.out.println("Thử bắn súng lần 2 ngay lập tức về hướng " + direction);
            } catch (IOException e) {
                System.out.println("Lỗi khi thử bắn súng lần 2: " + e.getMessage());
            }
            lastShotTime = System.currentTimeMillis();
            attacked = true;
        }

        // Ném vật phẩm nếu trong tầm và hết cooldown
        if (throwable != null && isInsideRange(throwable.getRange(), currentPosition, targetNode, direction)
                && System.currentTimeMillis() - lastThrowTime >= throwable.getCooldown() * STEP_TIME) {
            hero.throwItem(direction); // Ném lần 1
            System.out.println("Ném vật phẩm lần 1 về hướng " + direction);
            // Thử ném lần 2 để kiểm tra trick
            try {
                hero.throwItem(direction);
                System.out.println("Thử ném vật phẩm lần 2 ngay lập tức về hướng " + direction);
            } catch (IOException e) {
                System.out.println("Lỗi khi thử ném vật phẩm lần 2: " + e.getMessage());
            }
            lastThrowTime = System.currentTimeMillis();
            attacked = true;
        }

        // Dùng vũ khí đặc biệt nếu trong tầm và hết cooldown
        if (special != null && isInsideRange(special.getRange(), currentPosition, targetNode, direction)
                && System.currentTimeMillis() - lastSpecialTime >= special.getCooldown() * STEP_TIME) {
            hero.useSpecial(direction); // Dùng lần 1
            System.out.println("Dùng vũ khí đặc biệt lần 1 về hướng " + direction);
            // Thử dùng lần 2 để kiểm tra trick
            try {
                hero.useSpecial(direction);
                System.out.println("Thử dùng vũ khí đặc biệt lần 2 ngay lập tức về hướng " + direction);
            } catch (IOException e) {
                System.out.println("Lỗi khi thử dùng vũ khí đặc biệt lần 2: " + e.getMessage());
            }
            lastSpecialTime = System.currentTimeMillis();
            attacked = true;
        }

        // Tấn công cận chiến nếu trong tầm và hết cooldown
        if (isInsideRange(melee.getRange(), currentPosition, targetNode, direction)
                && System.currentTimeMillis() - lastMeleeTime >= melee.getCooldown() * STEP_TIME) {
            hero.attack(direction); // Tấn công lần 1
            System.out.println("Tấn công cận chiến lần 1 vào mục tiêu ở hướng " + direction);
            // Thử tấn công lần 2 để kiểm tra trick
            try {
                hero.attack(direction);
                System.out.println("Thử tấn công cận chiến lần 2 ngay lập tức ở hướng " + direction);
            } catch (IOException e) {
                System.out.println("Lỗi khi thử tấn công cận chiến lần 2: " + e.getMessage());
            }
            lastMeleeTime = System.currentTimeMillis();
            attacked = true;
        }

        return attacked; // Trả về true nếu tấn công thành công bằng cả 4 vũ khí
    }
}