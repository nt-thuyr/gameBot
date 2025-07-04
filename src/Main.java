import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
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

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        float maxHealth = hero.getGameMap().getCurrentPlayer().getHealth();

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                // Nếu máu dưới 50 và có vật phẩm hỗ trợ, hiển thị danh sách và sử dụng vật phẩm hồi máu phù hợp
                if (hero.getInventory().getListSupportItem() != null && gameMap.getCurrentPlayer().getHealth() < 50) {
                    try {
                        // Tính số máu hiện tại và máu tối đa
                        float currentHealth = gameMap.getCurrentPlayer().getHealth();
                        float lostHp = maxHealth - currentHealth; // Lượng máu cần hồi
                        // Tìm vật phẩm hồi máu phù hợp nhất
                        Element healingItem = findBestHealingItem(hero.getInventory().getListSupportItem(), lostHp);
                        if (healingItem != null) {
                            hero.useItem(healingItem.getId());
                            System.out.println("Đã sử dụng vật phẩm hồi máu: " + healingItem + ", máu hiện tại: " + gameMap.getCurrentPlayer().getHealth());
                        } else {
                            System.out.println("Không tìm thấy vật phẩm hồi máu phù hợp trong kho");
                        }
                    } catch (IOException e) {
                        System.out.println("Lỗi khi sử dụng vật phẩm hồi máu: " + e.getMessage());
                    }
                }

                if ("HAND".equals(hero.getInventory().getMelee().getId()) &&
                        hero.getInventory().getGun() == null &&
                        hero.getInventory().getThrowable() == null &&
                        hero.getInventory().getSpecial() == null) {
                    try {
                        pickUpNearestWeapon(hero, gameMap, true);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi nhặt vũ khí: " + e.getMessage());
                        // Fallback: Chuyển sang tấn công nếu nhặt thất bại
                        try {
                            attackWeakestPlayer(hero, gameMap);
                        } catch (IOException | InterruptedException ex) {
                            System.out.println("Lỗi khi tấn công sau thất bại nhặt vũ khí: " + ex.getMessage());
                        }
                    }
                    // Đã có vũ khí có thể dùng (gun có đạn, throwable, melee khác HAND)
                } else {
                    try {
                        attackTarget(hero, findWeakestPlayer(gameMap), gameMap);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi tấn công người chơi yếu nhất: " + e.getMessage());
                    }
                }
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    // Phương thức hỗ trợ để tìm vật phẩm hồi máu phù hợp nhất dựa trên lostHp
    private static SupportItem findBestHealingItem(List<SupportItem> supportItems, float lostHp) throws IOException {
        SupportItem bestItem = null;                // Lưu trữ vật phẩm tốt nhất
        float bestHealingHp = supportItems.get(0).getHealingHP();

        // TH1: nếu có vật phẩm hồi nhiều hơn lượng máu mất
        for (SupportItem item : supportItems) {
            if (item.getHealingHP() >= lostHp && item.getHealingHP() < bestHealingHp) {
                bestItem = item;
                bestHealingHp = item.getHealingHP();
            }
        }

        if (bestItem == null) {
            for (SupportItem item : supportItems) {
                if (item.getHealingHP() > bestHealingHp) {
                    bestItem = item;
                    bestHealingHp = item.getHealingHP();
                }
            }
        }

        return bestItem; // Trả về vật phẩm tốt nhất hoặc null nếu không có
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
            if (skipDarkArea && !checkInsideSafeArea(weaponNode, safeZone, mapSize)) {
                continue;
            }
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
        if (gun != null && dist <= rangeCalculator(gun.getRange()) && isStraightLine(currentPosition, targetNode)) {
            String direction = getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.shoot(direction);
                System.out.println("Bắn súng về hướng " + direction);
                return;
            }
        }

        // THROWABLE
        if (throwable != null && dist <= rangeCalculator(throwable.getRange()) && isStraightLine(currentPosition, targetNode)) {
            String direction = getStraightDirection(currentPosition, targetNode);
            if (direction != null) {
                hero.throwItem(direction);
                System.out.println("Ném vật phẩm về hướng " + direction);
                return;
            }
        }

        // SPECIAL
        if (special != null && dist <= rangeCalculator(special.getRange()) && isStraightLine(currentPosition, targetNode)) {
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

    public static Player selectPlayerTarget(Hero hero, GameMap gameMap) {
        Player weakest = findWeakestPlayer(gameMap);
        if (weakest != null && weakest.getHealth() < 100) {
            return weakest;
        }

        // Nếu tất cả đều đầy máu hoặc chỉ có weakestPlayer = full HP
        List<Player> players = gameMap.getOtherPlayerInfo();
        if (players == null || players.isEmpty()) return null;

        Node myPos = gameMap.getCurrentPlayer().getPosition();
        Player nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (Player p : players) {
            if (p.getHealth() <= 0 || p.getPosition() == null) continue;
            int dist = distance(myPos, p.getPosition());
            if (dist < minDist) {
                minDist = dist;
                nearest = p;
            }
        }

        return nearest;
    }

    public static void attackWeakestPlayer(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        Player target = selectPlayerTarget(hero, gameMap);
        if (target == null) {
            System.out.println("Không tìm thấy mục tiêu hợp lệ để tấn công!");
            return;
        }

        Node targetNode = target.getPosition();
        attackTarget(hero, targetNode, gameMap);
    }

    // Tìm vị trí ít người chơi nhất trong vùng an toàn
    private static void moveToLeastCrowdedPosition(Hero hero, GameMap gameMap) throws IOException, InterruptedException {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        List<Player> players = gameMap.getOtherPlayerInfo();
        int mapSize = gameMap.getMapSize();
        int safeZone = gameMap.getSafeZone();

        Node bestPosition = null;
        int minNearbyPlayers = Integer.MAX_VALUE;

        // Duyệt tất cả vị trí trên bản đồ
        for (int x = 0; x < mapSize; x++) {
            for (int y = 0; y < mapSize; y++) {
                Node candidate = new Node(x, y);
                if (!checkInsideSafeArea(candidate, safeZone, mapSize)) {
                    continue;
                }

                // Đếm số người chơi gần vị trí (trong phạm vi 5x5)
                int nearbyPlayers = 0;
                if (players != null) {
                    for (Player player : players) {
                        if (player.getHealth() <= 0 || player.getPosition() == null) {
                            continue;
                        }
                        int dist = distance(candidate, player.getPosition());
                        if (dist <= 2) {
                            nearbyPlayers++;
                        }
                    }
                }

                if (nearbyPlayers < minNearbyPlayers) {
                    minNearbyPlayers = nearbyPlayers;
                    bestPosition = candidate;
                }
            }
        }

        if (bestPosition == null) {
            System.out.println("Không tìm thấy vị trí phù hợp trong vùng an toàn!");
            return;
        }

        // Tính đường đi ngắn nhất đến vị trí ít người
        List<Node> restrictedNodes = getRestrictedNodes(gameMap);
        String path = getShortestPath(gameMap, restrictedNodes, currentPosition, bestPosition, true);

        if (path != null && !path.isEmpty()) {
            String step = path.substring(0, 1);
            hero.move(step);
            System.out.println("Di chuyển đến vị trí ít người: " + step);
        } else {
            System.out.println("Đã ở vị trí ít người hoặc không tìm thấy đường đi!");
        }
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
        List<Element> items = new ArrayList<>(gameMap.getListSupportItems());
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
        List<Element> items = new ArrayList<>(gameMap.getListSupportItems());
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

    private static int rangeCalculator(int[] x) {
        int range = 0;
        for (int i : x) {
            range += i * i;
        }
        return (int) Math.sqrt(range);
    }
}