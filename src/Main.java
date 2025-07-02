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
    private static final String GAME_ID = "188260";
    private static final String PLAYER_NAME = "thuyr";
    private static final String SECRET_KEY = "sk-bYZnqgHmR2GpG4ft9sTGiw:VPnqplsOhg3-sHpdn2C74nII8YdFlYIJjAVK9ynHS8tdJPlr5whr2ndgLZe9sC2qlfVyOw_65WxXwzSjBu0K8Q";




    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);


        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {


                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);


                if (hero.getInventory().getGun() != null && hero.getInventory().getGun().getBullet() == null) {
                    // Ưu tiên nhặt đạn nếu đã có súng mà chưa có đạn
                    try {
                        pickUpNearestBullet(hero, gameMap, true);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else if ("HAND".equals(hero.getInventory().getMelee().getId()) && hero.getInventory().getGun() == null
                        && hero.getInventory().getThrowable() == null && hero.getInventory().getSpecial() == null) {
                    // Không có gì cả, đi nhặt vũ khí
                    try {
                        pickUpNearestWeapon(hero, gameMap, false);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    // Đã có vũ khí có thể dùng (gun có đạn, throwable, melee khác HAND)
                    try {
                        destroyChest(hero, gameMap);
                        attackWeakestPlayer(hero, gameMap);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };


        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }


    private static void destroyChest(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        // 1. Lấy danh sách các rương trên bản đồ
        List<Obstacle> chests = gameMap.getListChests();
        if (chests == null || chests.isEmpty()) {
            System.out.println("Không tìm thấy rương nào trên bản đồ!");
            return;
        }


        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Weapon melee = hero.getInventory().getMelee();


        for (Obstacle chest : chests) {
            Node chestNode = chest.getPosition();
            if (chestNode == null) continue;


            // 2. Tính khoảng cách từ vị trí hiện tại đến rương
            int dist = distance(currentPosition, chestNode);
            System.out.println("Khoảng cách đến rương tại " + chestNode + ": " + dist);


            // Nếu rương đó ở gần (giả sử < 5 ô), di chuyển đến rương và phá
            if (dist < 10) {
                // Di chuyển đến rương nếu chưa kề cạnh
                if (dist > 1) {
                    List<Node> restrictedNodes = getRestrictedNodes(gameMap);
                    String path = getShortestPath(gameMap, restrictedNodes, currentPosition, chestNode, false);
                    if (path != null && !path.isEmpty()) {
                        String step = path.substring(0, 1); // Chỉ di chuyển 1 bước
                        hero.move(step);
                        System.out.println("Di chuyển 1 bước đến rương tại " + chestNode + " theo hướng: " + step);
                        return; // Thoát để chờ update vị trí mới
                    } else {
                        System.out.println("Không tìm thấy đường đi đến rương tại " + chestNode);
                        continue;
                    }
                }
                if (melee != null) {
                    String direction = getDirection(currentPosition, chestNode);
                    hero.attack(direction);
                    System.out.println("Đang tấn công rương tại " + chestNode + ", HP còn: " + chest.getHp());


                    // Kiểm tra HP và nhặt đồ nếu rương bị phá
                    if (chest.getHp() <= 0) {
                        pickUpNearbyItems(hero, gameMap, chestNode);
                        System.out.println("Đã phá rương và nhặt vật phẩm tại: " + chestNode);
                    }
                    return;
                }
                else {
                    System.out.println("Không có vũ khí cận chiến để phá rương tại " + chestNode);
                }
            } else {
                System.out.println("Rương tại " + chestNode + " quá xa, không phá.");
                continue;
            }


        }


    }


    private static void pickUpNearbyItems(Hero hero, GameMap gameMap, Node chestNode) throws IOException, InterruptedException {
        List<Node> itemPositions = new ArrayList<>();
        itemPositions.addAll(gameMap.getListWeapons());    // Vật phẩm vũ khí
        itemPositions.addAll(gameMap.getListBullets());    // Vật phẩm đạn
        itemPositions.addAll(gameMap.getListHealingItems()); // Vật phẩm hồi phục

        // Tạo danh sách các ô liền kề (bao gồm chính vị trí rương)
        List<Node> adjacentNodes = getAdjacentNodes(chestNode);


        // Duyệt các ô liền kề rương vừa phá (kể cả chính nó)
        for (Node itemNode : adjacentNodes) {
            // Nếu ô không phải ô vật phẩm thì bỏ qua
            if (!itemPositions.contains(itemNode)) {
                continue;
            }
            Node currentPosition = gameMap.getCurrentPlayer().getPosition();
            int dist = distance(currentPosition, itemNode);
            if (dist > 0) {
                List<Node> restrictedNodes = getRestrictedNodes(gameMap);
                String path = getShortestPath(gameMap, restrictedNodes, currentPosition, itemNode, false);
                if (path != null && !path.isEmpty()) {
                    String step = path.substring(0, 1); // Chỉ di chuyển 1 bước
                    hero.move(step);
                    System.out.println("Di chuyển 1 bước đến vật phẩm tại " + itemNode + " theo hướng: " + step);
                    return; // Thoát để chờ update vị trí mới
                } else {
                    System.out.println("Không tìm thấy đường đi đến vật phẩm tại " + itemNode);
                }
            } else {
                hero.pickupItem();
                System.out.println("Đã nhặt vật phẩm tại " + itemNode);
            }
        }


    }


    private static List<Node> getAdjacentNodes(Node node) {
        List<Node> adjacentNodes = new ArrayList<>();
        int x = node.getX();
        int y = node.getY();


        // Thêm các ô liền kề (8 ô xung quanh + chính ô rương)
        adjacentNodes.add(new Node(x, y)); // Vị trí rương
        adjacentNodes.add(new Node(x + 1, y)); // Phải
        adjacentNodes.add(new Node(x - 1, y)); // Trái
        adjacentNodes.add(new Node(x, y + 1)); // Trên
        adjacentNodes.add(new Node(x, y - 1)); // Dưới
        adjacentNodes.add(new Node(x + 1, y + 1)); // Trên phải
        adjacentNodes.add(new Node(x + 1, y - 1)); // Dưới phải
        adjacentNodes.add(new Node(x - 1, y + 1)); // Trên trái
        adjacentNodes.add(new Node(x - 1, y - 1)); // Dưới trái


        return adjacentNodes;
    }




    private static void pickUpNearestBullet(Hero hero, GameMap gameMap, boolean skipDarkArea) throws IOException, InterruptedException {
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


        List<Node> restrictedNodes = getRestrictedNodes(gameMap);
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


    public static void pickUpNearestWeapon(Hero hero, GameMap gameMap, boolean skipDarkArea) throws IOException, InterruptedException {
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
        List<Node> restrictedNodes = getRestrictedNodes(gameMap);
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


    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();
        // Né enemy trong vùng 3x3 (range = 1) hoặc 7x7 (range = 3) tùy yêu cầu
        int enemyRange = 1; // Nếu enemy có range = 3


        for (Enemy enemy : gameMap.getListEnemies()) {
            if (enemy.getPosition() != null) {
                Node pos = enemy.getPosition();
                for (int dx = -enemyRange; dx <= enemyRange; dx++) {
                    for (int dy = -enemyRange; dy <= enemyRange; dy++) {
                        restrictedNodes.add(new Node(pos.getX() + dx, pos.getY() + dy));
                    }
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


    public static void attackWeakestPlayer(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        // 1. Lấy danh sách tất cả người chơi
        List<Player> players = gameMap.getOtherPlayerInfo();


        // 2. Kiểm tra nếu danh sách rỗng
        if (players == null || players.isEmpty()) {
            System.out.println("Không tìm thấy người chơi nào trên bản đồ!");
            return;
        }


        // 3. Lấy vị trí hiện tại của người chơi
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();


        // 4. Tìm người chơi thấp máu nhất
        Float minHealth = Float.MAX_VALUE;
        Node targetNode = null;
        Player weakestPlayer = null;


        for (Player player : players) {
            if (player.getPosition() == null || player.getHealth() <= 0) continue;
            if (player.getHealth() < minHealth) {
                minHealth = player.getHealth();
                weakestPlayer = player;
                targetNode = player.getPosition();
            }
        }


        if (weakestPlayer == null) {
            System.out.println("Không tìm thấy người chơi nào hợp lệ để tấn công!");
            return;
        }


        int dist = distance(currentPosition, targetNode);
        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();
        Weapon melee = hero.getInventory().getMelee();
        Weapon special = hero.getInventory().getSpecial();


        // GUN
        if (gun != null && gun.getBullet() != null && dist <= gun.getRange() && isStraightLine(currentPosition, targetNode)) {
            String direction = getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.shoot(direction);
                System.out.println("Bắn súng về hướng " + direction);
                return;
            }
        }
        // THROWABLE
        if (throwable != null && dist <= throwable.getRange() && isStraightLine(currentPosition, targetNode)) {
            String direction = getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.throwItem(direction, dist);
                System.out.println("Ném vật phẩm về hướng " + direction);
                return;
            }
        }
        // SPECIAL
        if (special != null && dist <= special.getRange() && isStraightLine(currentPosition, targetNode)) {
            String direction = getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.useSpecial(direction);
                System.out.println("Dùng vũ khí đặc biệt về hướng " + direction);
                return;
            }
        }
        // MELEE
        if (melee != null && !"HAND".equals(melee.getId()) && dist == 1) { // Nếu có vũ khí cận chiến khác HAND và kề cạnh
            String direction = getDirection(currentPosition, targetNode);
            hero.attack(direction);
            System.out.println("Tấn công cận chiến về hướng " + direction);
            return;
        }


        System.out.println("Không đủ điều kiện tấn công!");


        // Nếu chưa trong phạm vi, di chuyển 1 bước về phía mục tiêu
        // Tìm đường đi ngắn nhất
        List<Node> restrictedNodes = getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến người chơi gần nhất!");
            return;
        }
        // Chỉ di chuyển 1 bước đầu tiên trong path
        String step = path.substring(0, 1);
        hero.move(step);
        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
    }


    // Hàm xác định hướng (direction) từ node hiện tại đến node mục tiêu
    public static String getDirection(Node from, Node to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == 1) return "u";    // đảo lại: dy == 1 là lên
        if (dx == 0 && dy == -1) return "d";   // đảo lại: dy == -1 là xuống
        if (dx == -1 && dy == 0) return "l";
        if (dx == 1 && dy == 0) return "r";
        // Nếu mục tiêu không kề cạnh, ưu tiên hướng chính
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? "r" : "l";
        } else if (Math.abs(dy) > 0) {
            return dy > 0 ? "u" : "d";  // đảo tương tự cho hướng xa
        }
        return "d"; // fallback
    }


    public static String getStraightDirection(Node from, Node to) {
        if (from.getX() == to.getX()) {
            return (to.getY() > from.getY()) ? "u" : "d";
        }
        if (from.getY() == to.getY()) {
            return (to.getX() > from.getX()) ? "r" : "l";
        }
        return null; // không cùng hàng/cột
    }


    public static boolean isStraightLine(Node from, Node to) {
        return from.getX() == to.getX() || from.getY() == to.getY();
    }
}

