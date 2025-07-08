import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static jsclub.codefest.sdk.algorithm.PathUtils.checkInsideSafeArea;
import static jsclub.codefest.sdk.algorithm.PathUtils.distance;

public class Attack {

    private static long lastShotTime = 0;       // Thời điểm bắn súng gần nhất
    private static long lastThrowTime = 0;      // Thời điểm ném gần nhất
    private static long lastSpecialTime = 0;    // Thời điểm sử dụng vũ khí đặc biệt gần nhất
    private static long lastMeleeTime = 0;      // Thời điểm tấn công cận chiến gần nhất
    private static final long STEP_TIME = 500;  // Thời gian 1 step game là 500ms

    static boolean isInsideRange(GameMap gameMap, Weapon weapon, Node from, Node to, String direction) {
        int width, depth, halfWidth, halfDepth;
        int playerX = from.getX();
        int playerY = from.getY();
        int targetX = to.getX();
        int targetY = to.getY();

        if (weapon.getType().equals(ElementType.THROWABLE)) {
            width = depth = weapon.getExplodeRange();
            halfWidth = width / 2;
            halfDepth = depth / 2;

            int throwRange = weapon.getRange()[1];

            return switch (direction) {
                case "u" -> // hướng lên, y tăng
                        (targetY > playerY + throwRange - halfDepth && targetY <= playerY + throwRange + halfDepth) &&
                                (targetX >= playerX - halfWidth && targetX <= playerX + halfWidth);
                case "d" -> // hướng xuống, y giảm
                        (targetY < playerY - throwRange + halfDepth && targetY >= playerY - throwRange - halfDepth) &&
                                (targetX >= playerX - halfWidth && targetX <= playerX + halfWidth);
                case "l" -> // hướng trái, x giảm
                        (targetX < playerX - throwRange + halfDepth && targetX >= playerX - throwRange - halfDepth) &&
                                (targetY >= playerY - halfWidth && targetY <= playerY + halfWidth);
                case "r" -> // hướng phải, x tăng
                        (targetX > playerX + throwRange - halfDepth && targetX <= playerX + throwRange + halfDepth) &&
                                (targetY >= playerY - halfWidth && targetY <= playerY + halfWidth);
                default -> false; // hướng không hợp lệ
            };
        } else {
            // Kiểm tra chướng ngại vật trên đường bắn
            for (int y = playerY; y <= targetY; y++) {
                for (int x = playerX; x <= targetX; x++) {
                    Element obstacle = gameMap.getElementByIndex(x, y);
                    if (obstacle instanceof Obstacle && !((Obstacle) obstacle).getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                        return false; // Có chướng ngại vật trên đường bắn
                    }
                }
            }

            width = weapon.getRange()[0];
            depth = weapon.getRange()[1];
            halfWidth = width / 2;

            return switch (direction) {
                case "u" -> // hướng lên, y tăng
                        (targetY > playerY && targetY <= playerY + depth) &&
                                (targetX >= playerX - halfWidth && targetX <= playerX + halfWidth);
                case "d" -> // hướng xuống, y giảm
                        (targetY < playerY && targetY >= playerY - depth) &&
                                (targetX >= playerX - halfWidth && targetX <= playerX + halfWidth);
                case "l" -> // hướng trái, x giảm
                        (targetX < playerX && targetX >= playerX - depth) &&
                                (targetY >= playerY - halfWidth && targetY <= playerY + halfWidth);
                case "r" -> // hướng phải, x tăng
                        (targetX > playerX && targetX <= playerX + depth) &&
                                (targetY >= playerY - halfWidth && targetY <= playerY + halfWidth);
                default -> false; // hướng không hợp lệ
            };
        }
    }


    // Hàm tìm người chơi yếu máu nhất
    // Tìm player yếu máu nhất, nếu đều bằng nhau thì trả về null
    static Player findWeakestPlayer(GameMap gameMap) {
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
    static Player findNearestPlayer(GameMap gameMap, Node currentPosition) {
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

    // Thử tấn công mục tiêu bằng các vũ khí có thể
    static boolean attackTarget(Hero hero, Player targetPlayer, GameMap gameMap) throws IOException {
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

        // Ném vật phẩm nếu trong tầm và hết cooldown
        if (throwable != null && isInsideRange(gameMap, throwable, currentPosition, targetNode, direction)
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
        if (special != null && isInsideRange(gameMap, special, currentPosition, targetNode, direction)
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

        // Bắn súng nếu trong tầm và hết cooldown
        if (gun != null && isInsideRange(gameMap, gun, currentPosition, targetNode, direction)
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

        // Tấn công cận chiến nếu trong tầm và hết cooldown
        if (isInsideRange(gameMap, melee, currentPosition, targetNode, direction)
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

        return attacked; // Trả về true nếu tấn công thành công bằng 1 vũ khí
    }
}