import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Bullet;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.*;


public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "189774";
    private static final String PLAYER_NAME = "thuyr";
    private static final String SECRET_KEY = "sk-FgDKAR8dR8-9jufLyCkmwA:WZwQnHWBZddohDzbq_MwfDAJCZOuORtYyCri1-052o0aj2Fd35dWuLY4XOmPgjB810f8OnAQrDWTGvuKUc4lFQ";


    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                List<Node> restrictedNodes = getRestrictedNodes(gameMap, hero);

                if (hero.getInventory().getGun() != null && hero.getInventory().getGun().getBullet() == null) {
                    // Ưu tiên nhặt đạn nếu đã có súng mà chưa có đạn
                    try {
                        pickUpNearestBullet(hero, gameMap, restrictedNodes, true);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else if ("HAND".equals(hero.getInventory().getMelee().getId()) && hero.getInventory().getGun() == null
                        && hero.getInventory().getThrowable() == null && hero.getInventory().getSpecial() == null) {
                    // Không có gì cả, đi nhặt vũ khí
                    try {
                        pickUpNearestWeapon(hero, gameMap, restrictedNodes, false);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
//                } else {
//                    // Đã có vũ khí có thể dùng (gun có đạn, throwable, melee khác HAND)
//                    try {
//                        attackWeakestPlayer(hero, gameMap, restrictedNodes);
//                    } catch (IOException | InterruptedException e) {
//                        throw new RuntimeException(e);
//                    }
                }

            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    private static void pickUpNearestBullet(Hero hero, GameMap gameMap, List<Node> restrictedNodes, boolean skipDarkArea) throws IOException, InterruptedException {
        // 1. Lấy danh sách các hộp đạn trên bản đồ
        List<Bullet> bullets = gameMap.getListBullets();
        if (bullets == null || bullets.isEmpty()) {
            System.out.println("Không tìm thấy hộp đạn nào trên bản đồ!");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();

        Bullet nearestBullet = null;
        int minDistance = Integer.MAX_VALUE;
        Node nearestNode = null;

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Bullet bullet : bullets) {
            Node bulletNode = bullet.getPosition();
            if (!skipDarkArea && !checkInsideSafeArea(bulletNode, safeZone, mapSize)) {
                continue;
            }
            int dist = distance(currentPosition, bulletNode);
            if (dist < minDistance) {
                minDistance = dist;
                nearestBullet = bullet;
                nearestNode = bulletNode;
            }
        }

        if (nearestBullet == null) {
            System.out.println("Không tìm thấy hộp đạn phù hợp trong vùng an toàn!");
            return;
        }

        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, nearestNode, skipDarkArea);

        // Nếu không tìm thấy đường đi, kiểm tra lại có phải đã ở vị trí hộp đạn chưa
        if (path != null && !path.isEmpty()) {
            hero.move(path);
            System.out.println("Di chuyển: " + path);
        } else {
            // Nếu đã đứng trên vị trí hộp đạn thì nhặt luôn, không di chuyển
            hero.pickupItem();
            System.out.println("Đang đứng trên hộp đạn, thực hiện nhặt.");
        }
    }

    public static void pickUpNearestWeapon(Hero hero, GameMap gameMap, List<Node> restrictedNodes, boolean skipDarkArea) throws IOException, InterruptedException {
        List<Weapon> weapons = gameMap.getListWeapons();
        if (weapons == null || weapons.isEmpty()) {
            System.out.println("Không tìm thấy vũ khí trên bản đồ!");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();

        Weapon nearestWeapon = null;
        int minDistance = Integer.MAX_VALUE;
        Node nearestNode = null;

        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        for (Weapon weapon : weapons) {
            Node weaponNode = weapon.getPosition();
            if (!skipDarkArea && !checkInsideSafeArea(weaponNode, safeZone, mapSize)) continue;
            int dist = distance(currentPosition, weaponNode);
            if (dist < minDistance) {
                minDistance = dist;
                nearestWeapon = weapon;
                nearestNode = weaponNode;
            }
        }

        if (nearestWeapon == null) {
            System.out.println("Không tìm thấy vũ khí hợp lệ trong vùng an toàn!");
            return;
        }

        // Nếu chưa ở vị trí vũ khí, tính đường đi
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, nearestNode, skipDarkArea);
        System.out.println("Path tìm được: " + path);

        if (path != null && !path.isEmpty()) {
            hero.move(path);
            System.out.println("Di chuyển: " + path);
        } else {
            // Nếu đã đứng trên vị trí vũ khí thì nhặt luôn, không di chuyển
            hero.pickupItem();
            System.out.println("Đang đứng trên vũ khí, thực hiện nhặt.");
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap, Hero hero) {
        List<Node> restrictedNodes = new ArrayList<>();

        boolean isUnarmed = (
                hero.getInventory().getGun() == null &&
                        hero.getInventory().getThrowable() == null &&
                        hero.getInventory().getSpecial() == null &&
                        "HAND".equals(hero.getInventory().getMelee().getId())
        );

        // Nếu đang tay không → né enemy nguy hiểm (damage > 20) và vùng xung quanh
        if (isUnarmed) {
            for (Enemy enemy : gameMap.getListEnemies()) {
                if (enemy.getPosition() != null && enemy.getDamage() > 20) {
                    Node pos = enemy.getPosition();

                    // Thêm vị trí enemy
                    restrictedNodes.add(pos);

                    // Thêm các ô xung quanh
                    restrictedNodes.add(new Node(pos.getX() + 1, pos.getY()));
                    restrictedNodes.add(new Node(pos.getX() - 1, pos.getY()));
                    restrictedNodes.add(new Node(pos.getX(), pos.getY() + 1));
                    restrictedNodes.add(new Node(pos.getX(), pos.getY() - 1));
                }
            }
        }

        // Tránh chướng ngại vật
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (obstacle.getPosition() != null) {
                restrictedNodes.add(obstacle.getPosition());
            }
        }

        // Tránh người chơi khác còn sống
        for (Player player : gameMap.getOtherPlayerInfo()) {
            if (player.getPosition() != null && player.getHealth() > 0) {
                restrictedNodes.add(player.getPosition());
            }
        }

        return restrictedNodes;
    }



//    public static void attackWeakestPlayer(Hero hero, GameMap gameMap, List<Node> restrictedNodes) throws IOException, InterruptedException {
//        // 1. Lấy danh sách tất cả người chơi
//        List<Player> players = gameMap.getOtherPlayerInfo();
//
//        // 2. Kiểm tra nếu danh sách rỗng
//        if (players == null || players.isEmpty()) {
//            System.out.println("Không tìm thấy người chơi nào trên bản đồ!");
//            return;
//        }
//
//        // 3. Lấy vị trí hiện tại của người chơi
//        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
//
//        // 4. Tìm người chơi thấp máu nhất
//        Float minHealth = Float.MAX_VALUE;
//        Node targetNode = null;
//        Player weakestPlayer = null;
//
//        for (Player player : players) {
//            if (player.getPosition() == null || player.getHealth() <= 0) continue;
//            if (player.getHealth() < minHealth) {
//                minHealth = player.getHealth();
//                weakestPlayer = player;
//                targetNode = player.getPosition();
//            }
//        }
//
//        if (weakestPlayer == null) {
//            System.out.println("Không tìm thấy người chơi nào hợp lệ để tấn công!");
//            return;
//        }
//
//        int dist = distance(currentPosition, targetNode);
//        Weapon gun = hero.getInventory().getGun();
//        Weapon throwable = hero.getInventory().getThrowable();
//        Weapon melee = hero.getInventory().getMelee();
//        Weapon special = hero.getInventory().getSpecial();
//
//        // GUN
//        if (gun != null && gun.getBullet() != null && dist <= gun.getRange() && isStraightLine(currentPosition, targetNode)) {
//            String direction = getStraightDirection(currentPosition, targetNode);
//            if (direction != null) {
//                hero.shoot(direction);
//                System.out.println("Bắn súng về hướng " + direction);
//                return;
//            }
//        }
//        // THROWABLE
//        if (throwable != null && dist <= throwable.getRange() && isStraightLine(currentPosition, targetNode)) {
//            String direction = getStraightDirection(currentPosition, targetNode);
//            if (direction != null) {
//                hero.throwItem(direction, dist);
//                System.out.println("Ném vật phẩm về hướng " + direction);
//                return;
//            }
//        }
//        // SPECIAL
//        if (special != null && dist <= special.getRange() && isStraightLine(currentPosition, targetNode)) {
//            String direction = getStraightDirection(currentPosition, targetNode);
//            if (direction != null) {
//                hero.useSpecial(direction);
//                System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
//                return;
//            }
//        }
//        // MELEE
//        if (melee != null && !"HAND".equals(melee.getId()) && dist == 1) {
//            String direction = getDirection(currentPosition, targetNode);
//            hero.attack(direction);
//            System.out.println("Tấn công cận chiến về hướng " + direction);
//            return;
//        }
//
//        System.out.println("Không đủ điều kiện tấn công!");
//
//        // Nếu chưa trong phạm vi, di chuyển 1 bước về phía mục tiêu
//        // Tìm đường đi ngắn nhất
//        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
//        if (path == null || path.isEmpty()) {
//            System.out.println("Không tìm thấy đường đi đến người chơi gần nhất!");
//            return;
//        }
//        // Chỉ di chuyển 1 bước đầu tiên trong path
//        String step = path.substring(0, 1);
//        hero.move(step);
//        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
//    }
//
//    // Hàm xác định hướng (direction) từ node hiện tại đến node mục tiêu
//    public static String getDirection(Node from, Node to) {
//        int dx = to.getX() - from.getX();
//        int dy = to.getY() - from.getY();
//        if (dx == 0 && dy == -1) return "u";
//        if (dx == 0 && dy == 1) return "d";
//        if (dx == -1 && dy == 0) return "l";
//        if (dx == 1 && dy == 0) return "r";
//        // Nếu mục tiêu không kề cạnh, ưu tiên hướng chính
//        if (Math.abs(dx) > Math.abs(dy)) {
//            return dx > 0 ? "r" : "l";
//        } else if (Math.abs(dy) > 0) {
//            return dy > 0 ? "d" : "u";
//        }
//        return "d"; // fallback
//    }
//
//    public static String getStraightDirection(Node from, Node to) {
//        if (from.getX() == to.getX()) {
//            return (to.getY() > from.getY()) ? "d" : "u";
//        }
//        if (from.getY() == to.getY()) {
//            return (to.getX() > from.getX()) ? "r" : "l";
//        }
//        return null; // không cùng hàng/cột
//    }
//
//    public static boolean isStraightLine(Node from, Node to) {
//        return from.getX() == to.getX() || from.getY() == to.getY();
//    }
}