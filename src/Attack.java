import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.model.Inventory;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static jsclub.codefest.sdk.algorithm.PathUtils.distance;

public class Attack {

    private static long lastShotStep = 0;       // Thời điểm bắn súng gần nhất
    private static long lastThrowStep = 0;      // Thời điểm ném gần nhất
    private static long lastSpecialStep = 0;    // Thời điểm sử dụng vũ khí đặc biệt gần nhất
    private static long lastMeleeStep = 0;      // Thời điểm tấn công cận chiến gần nhất

    static int currentDamage(Hero hero) {
        Inventory inventory = hero.getInventory();
        int dmg = 0;
        if (inventory.getMelee() != null) dmg += inventory.getMelee().getDamage();
        if (inventory.getGun() != null) dmg += inventory.getGun().getDamage();
//        if (inventory.getThrowable() != null) dmg += inventory.getThrowable().getDamage();
        if (inventory.getSpecial() != null) dmg += inventory.getSpecial().getDamage();
        System.out.println("Current damage: " + dmg);
        return dmg;
    }

    static int currentDefense(Hero hero) {
        Inventory inventory = hero.getInventory();
        int def = 0;
        if (inventory.getArmor() != null) def += inventory.getArmor().getDamageReduce();
        if (inventory.getHelmet() != null) def += inventory.getHelmet().getDamageReduce();
        System.out.println("Current defense: " + def);
        return def;
    }

    static int currentHealing(Hero hero) {
        Inventory inventory = hero.getInventory();
        int heal = 0;
        if (inventory.getListSupportItem() != null) {
            for (SupportItem item : inventory.getListSupportItem()) {
                heal += item.getHealingHP();
            }
        }
        System.out.println("Current healing: " + heal);
        return heal;
    }

    static boolean isInsideRange(GameMap gameMap, Weapon weapon, Node from, Node to, String direction) {
        if (weapon == null || from == null || to == null || direction == null) {
            return false; // Kiểm tra các tham số hợp lệ
        }

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
            if (direction.equals("r")) {
                for (int x = playerX + 1; x < targetX; x++) {
                    Element e = gameMap.getElementByIndex(x, playerY);
                    if (e instanceof Obstacle && !((Obstacle) e).getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                        return false;
                    }
                }
            } else if (direction.equals("l")) {
                for (int x = targetX + 1; x < playerX; x++) {
                    Element e = gameMap.getElementByIndex(x, playerY);
                    if (e instanceof Obstacle && !((Obstacle) e).getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                        return false;
                    }
                }
            } else if (direction.equals("u")) {
                for (int y = playerY + 1; y < targetY; y++) {
                    Element e = gameMap.getElementByIndex(playerX, y);
                    if (e instanceof Obstacle && !((Obstacle) e).getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                        return false;
                    }
                }
            } else if (direction.equals("d")) {
                for (int y = targetY + 1; y < playerY; y++) {
                    Element e = gameMap.getElementByIndex(playerX, y);
                    if (e instanceof Obstacle && !((Obstacle) e).getTags().contains(ObstacleTag.CAN_SHOOT_THROUGH)) {
                        return false;
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
    static Player findWeakestPlayer(GameMap gameMap, int range, Node currentPosition) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Player weakest = null;
        float minHealth = Float.MAX_VALUE;
        boolean allEqual = true;
        Float firstHp = null;

        for (Player p : players) {
            if (p.getPosition() == null || p.getHealth() <= 0) continue;
            if (distance(currentPosition, p.getPosition()) > range) continue;
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
        if (isInsideRange(gameMap, throwable, currentPosition, targetNode, direction)
                && gameMap.getStepNumber() - lastThrowStep >= throwable.getCooldown()) {
            hero.throwItem(direction);
            System.out.println("Ném vật phẩm về hướng " + direction);
            lastThrowStep = gameMap.getStepNumber();
            attacked = true;
        }

        // Dùng vũ khí đặc biệt nếu trong tầm và hết cooldown
        if (isInsideRange(gameMap, special, currentPosition, targetNode, direction)
                && gameMap.getStepNumber() - lastSpecialStep >= special.getCooldown()) {
            // Không sử dụng rope với kẻ địch ở gần
            if ((special.getId().equals("ROPE") && distance(currentPosition, targetNode) > 2 &&
                    (gun.getId().equals("SHOTGUN") || melee.getDamage() >= 40)) ||
                    !special.getId().equals("ROPE")) {
                hero.useSpecial(direction);
                System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
                lastSpecialStep = gameMap.getStepNumber();
                attacked = true;
            }
        }

        // Bắn súng nếu trong tầm và hết cooldown
        if (isInsideRange(gameMap, gun, currentPosition, targetNode, direction)
                && gameMap.getStepNumber() - lastShotStep >= gun.getCooldown()) {
            hero.shoot(direction);
            System.out.println("Bắn súng về hướng " + direction);
            lastShotStep = gameMap.getStepNumber();
            attacked = true;
        }

        // Tấn công cận chiến nếu trong tầm và hết cooldown
        // Nếu trong tầm cận chiến mà không có vũ khí cận chiến thì sao?
        if (isInsideRange(gameMap, melee, currentPosition, targetNode, direction)
                && gameMap.getStepNumber() - lastMeleeStep >= melee.getCooldown()) {
            // Nếu không có vũ khí cận chiến thì sử dụng vũ khí khác
            if ("HAND".equals(melee.getId())) {
                // Ném vật phẩm nếu trong tầm và hết cooldown
                if (isInsideRange(gameMap, throwable, currentPosition, targetNode, direction)
                        && gameMap.getStepNumber() - lastThrowStep >= throwable.getCooldown()) {
                    hero.throwItem(direction);
                    System.out.println("Ném vật phẩm về hướng " + direction);
                    lastThrowStep = gameMap.getStepNumber();
                    attacked = true;
                }
                // Dùng vũ khí đặc biệt nếu trong tầm và hết cooldown
                else if (isInsideRange(gameMap, special, currentPosition, targetNode, direction)
                        && gameMap.getStepNumber() - lastSpecialStep >= special.getCooldown()) {
                    // Không sử dụng rope với kẻ địch ở gần
                    if ((special.getId().equals("ROPE") && distance(currentPosition, targetNode) > 2 &&
                            (gun.getId().equals("SHOTGUN") || melee.getDamage() >= 40)) ||
                            !special.getId().equals("ROPE")) {
                        hero.useSpecial(direction);
                        System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
                        lastSpecialStep = gameMap.getStepNumber();
                        attacked = true;
                    }
                }
                // Bắn súng nếu trong tầm và hết cooldown
                else if (isInsideRange(gameMap, gun, currentPosition, targetNode, direction)
                        && gameMap.getStepNumber() - lastShotStep >= gun.getCooldown()) {
                    hero.shoot(direction);
                    System.out.println("Bắn súng về hướng " + direction);
                    lastShotStep = gameMap.getStepNumber();
                    attacked = true;
                }
                // Nếu không còn vũ khí nào khác
                else {
                    hero.attack(direction);
                    System.out.println("Tấn công cận chiến về hướng " + direction);
                    lastMeleeStep = gameMap.getStepNumber();
                    attacked = true;
                }


            }
            // Nếu có vũ khí cận chiến
            else {
                hero.attack(direction);
                System.out.println("Tấn công cận chiến về hướng " + direction);
                lastMeleeStep = gameMap.getStepNumber();
                attacked = true;
            }
        }

        return attacked; // Trả về true nếu tấn công thành công bằng 1 vũ khí
    }

     // Kiểm tra xem có player nào gần vị trí hiện tại không
    static Player checkIfHasNearbyPlayer(GameMap gameMap, int radius) {
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();

        for (Player player : players) {
            if (player.getHealth() <= 0) continue; // Bỏ qua player đã chết
            if (distance(currentPosition, player.getPosition()) <= radius) {
                return player; // Trả về player gần nhất
            }
        }
        return null; // Không có player nào gần
    }

    static boolean isCombatReady(Hero hero) {
        // Kiểm tra trang bị
        return  (currentDamage(hero) >= 40 && currentDefense(hero) >= 20 &&
                (currentHealing(hero) >= 20 || hero.getInventory().getListSupportItem().size() == 4) &&
                hero.getGameMap().getCurrentPlayer().getHealth() >= 80);

    }
}