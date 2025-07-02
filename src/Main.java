import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.armors.Armor;
import jsclub.codefest.sdk.model.healing_items.HealingItem;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.weapon.Weapon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static jsclub.codefest.sdk.algorithm.PathUtils.*;


public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "115663";
    private static final String PLAYER_NAME = "thuyr";
    private static final String SECRET_KEY = "sk-bYZnqgHmR2GpG4ft9sTGiw:VPnqplsOhg3-sHpdn2C74nII8YdFlYIJjAVK9ynHS8tdJPlr5whr2ndgLZe9sC2qlfVyOw_65WxXwzSjBu0K8Q";
    
    private static Node lastEggPosition = null;

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                // Kiểm tra trứng rồng nếu có
                boolean eggDropped = false;
                Obstacle egg = null;

                List<Obstacle> chests = gameMap.getListObstacles();
                for (Obstacle chest : chests) {
                    if ("DRAGON_EGG".equals(chest.getId()) && chest.getPosition() != null) {
                        egg = chest;
                        eggDropped = true;
                        break; // Đã tìm thấy egg, thoát vòng lặp
                    }
                }
                if (eggDropped) {
                    lastEggPosition = egg.getPosition();
                    try {
                        openDragonEgg(hero, gameMap, egg);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    return; // QUAN TRỌNG: return để dừng, không làm gì thêm ở tick này!
                }
                // Nếu vừa phá xong trứng (không còn egg, nhưng lastEggPosition vẫn còn)
                if (lastEggPosition != null) {
                    // Kiểm tra còn item quanh vị trí lastEggPosition không?
                    boolean hasItemNearby = hasPickupableItemAround(lastEggPosition, gameMap);
                    if (hasItemNearby) {
                        try {
                            pickupItemAround(hero, gameMap, lastEggPosition);
                        } catch (IOException | InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        return; // Ưu tiên nhặt item quanh trứng, xong mới làm việc khác
                    } else {
                        lastEggPosition = null; // Không còn item quanh trứng, xóa vị trí
                    }
                }

                // Nếu không có egg hoặc đã phá xong egg, làm các hành động khác
                if ("HAND".equals(hero.getInventory().getMelee().getId()) && hero.getInventory().getGun() == null
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
                        attackTarget(hero, findWeakestPlayer(gameMap), gameMap);
                    } catch (IOException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
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
            String step = path.substring(0, 1);
            hero.move(step);
            System.out.println("Di chuyển: " + path);
        } else {
            // Nếu đã đứng trên vị trí vũ khí thì nhặt luôn, không di chuyển
            hero.pickupItem();
            System.out.println("Đang đứng trên vũ khí, thực hiện nhặt.");
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();

        int enemyRange = 2; // Nếu enemy có range = 1, thì sẽ tránh các 3x3 ô xung quanh nó
        // set enemyRange = 2 để đề phòng enemy di chuyển đến gần mình
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

        // MELEE – Chỉ dùng khi sát bên
        if (melee != null && !"HAND".equals(melee.getId()) && dist == 1) {
            String direction = getDirection(currentPosition, targetNode);
            hero.attack(direction);
            System.out.println("Tấn công cận chiến vào mục tiêu ở hướng " + direction);
            return;
        }

        System.out.println("Không thể tấn công mục tiêu!");

        // Di chuyển nếu không đủ điều kiện tấn công
        List<Node> restrictedNodes = getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến mục tiêu!");
            return;
        }

        String step = path.substring(0, 1);
        hero.move(step);
        System.out.println("Di chuyển 1 bước về hướng " + step + " để tiếp cận mục tiêu.");
    }

    // Hàm xác định hướng (direction) từ node hiện tại đến node mục tiêu
    public static String getDirection(Node from, Node to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx == 0 && dy == 1) return "u";
        if (dx == 0 && dy == -1) return "d";
        if (dx == -1 && dy == 0) return "l";
        if (dx == 1 && dy == 0) return "r";
        // Nếu mục tiêu không kề cạnh, ưu tiên hướng chính
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0 ? "r" : "l";
        } else if (Math.abs(dy) > 0) {
            return dy > 0 ? "u" : "d";
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

    public static void openDragonEgg(Hero hero, GameMap gameMap, Obstacle egg) throws IOException, InterruptedException {
        if (egg == null || egg.getPosition() == null) {
            System.out.println("Rương rồng không hợp lệ hoặc không có vị trí.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Node eggPosition = egg.getPosition();

        int dist = distance(currentPosition, eggPosition);

        if (dist == 1) {
            // Ưu tiên tấn công cận chiến
            String direction = getDirection(currentPosition, eggPosition);
            hero.attack(direction);
            System.out.println("Tấn công rương rồng ở hướng " + direction);
        } else {
            // Nếu không ở gần, di chuyển đến rương
            List<Node> restrictedNodes = getRestrictedNodes(gameMap);
            String path = getShortestPath(gameMap, restrictedNodes, currentPosition, eggPosition, false);
            if (path != null && !path.isEmpty()) {
                String step = path.substring(0, 1);
                hero.move(step);
                System.out.println("Di chuyển đến rương rồng: " + path);
            } else {
                System.out.println("Không tìm thấy đường đến rương rồng!");
            }
        }
    }

    public static void pickupItemAround(Hero hero, GameMap gameMap, Node center) throws IOException, InterruptedException {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Element> items = new ArrayList<>(gameMap.getListHealingItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());

        Element nearestItem = null;
        int minDist = Integer.MAX_VALUE;
        for (Element item : items) {
            if (item.getPosition() != null && pickupable(item)) {
                int dist = distance(center, item.getPosition());
                if (dist < minDist && dist <= 5) {
                    minDist = dist;
                    nearestItem = item;
                }
            }
        }

        if (nearestItem != null) {
            List<Node> restrictedNodes = getRestrictedNodes(gameMap);
            String path = getShortestPath(gameMap, restrictedNodes, currentPosition, nearestItem.getPosition(), false);
            if (path != null && !path.isEmpty()) {
                String step = path.substring(0, 1);
                hero.move(step);
                System.out.println("Di chuyển đến vật phẩm: " + path);
            } else {
                hero.pickupItem();
                System.out.println("Đã nhặt vật phẩm: " + nearestItem.getId());
            }
        }
    }

    private static boolean hasPickupableItemAround(Node center, GameMap gameMap) {
        List<Element> items = new ArrayList<>(gameMap.getListHealingItems());
        items.addAll(gameMap.getListWeapons());
        items.addAll(gameMap.getListArmors());
        for (Element item : items) {
            if (item.getPosition() != null && distance(center, item.getPosition()) <= 5) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra chỉ số của item xem có nên nhặt không
    private static boolean pickupable(Element item) {
        return true;
    }
}