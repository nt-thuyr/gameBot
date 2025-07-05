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
    private static final String GAME_ID = "108644";
    private static final String PLAYER_NAME = "vicky";
    private static final String SECRET_KEY = "sk-bYZnqgHmR2GpG4ft9sTGiw:VPnqplsOhg3-sHpdn2C74nII8YdFlYIJjAVK9ynHS8tdJPlr5whr2ndgLZe9sC2qlfVyOw_65WxXwzSjBu0K8Q";

    static InventoryManager invManager = new InventoryManager();
    static Node lastChestPosition = null;
    static Obstacle lastChest = null;
    static Player lockedTarget = null;
    static final float maxHealth = 100.0f; // Giả sử máu tối đa là 100

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        InventoryManager invManager = new InventoryManager();

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                float currentHealth = gameMap.getCurrentPlayer().getHealth();
                System.out.println("Current Health: " + currentHealth);

                // 1. Nếu không có vật phẩm hồi máu, ưu tiên mở rương và loot đồ
                if (hero.getInventory().getListSupportItem().isEmpty()) {
                    Obstacle targetChest = invManager.getNearestChest(gameMap, hero);
                    if (targetChest != null) {
                        lastChestPosition = new Node(targetChest.getX(), targetChest.getY());
                        lastChest = targetChest;
                        try {
                            invManager.openChest(gameMap, hero, targetChest);
                        } catch (IOException e) {
                            System.out.println("Lỗi khi mở rương: " + e.getMessage());
                        }
                        return;
                    }

                    if (lastChestPosition != null) {
                        boolean hasItemNearby = InventoryManager.hasPickupableItemAround(lastChestPosition, gameMap);
                        if (hasItemNearby) {
                            invManager.lootDroppedItems(gameMap, hero, lastChest);
                            return;
                        } else {
                            lastChestPosition = null; // Không còn item quanh, reset
                        }
                    }
                }

                // 2. Nếu đang lock target thì tấn công cho đến khi tiêu diệt hoặc không còn tấn công được
                if (lockedTarget != null && lockedTarget.getHealth() > 0) {
                    try {
                        Attack.attackTarget(hero, lockedTarget, gameMap);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi tấn công target: " + e.getMessage());
                    }
                    return;
                } else {
                    lockedTarget = null;
                }

                // 3. Nếu máu yếu, ưu tiên hồi máu
                if (currentHealth < maxHealth * 0.8f) {
                    Element healingItem = findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
                    if (healingItem != null) {
                        try {
                            hero.useItem(healingItem.getId());
                            System.out.println("Đã sử dụng vật phẩm hồi máu: " + healingItem + ", máu hiện tại: " + currentHealth);
                        } catch (IOException e) {
                            System.out.println("Lỗi khi sử dụng vật phẩm hồi máu: " + e.getMessage());
                        }
                        return;
                    }
                }

                // 4. Tìm đối thủ yếu máu nhất để lock target và săn
                Player weakest = Attack.findWeakestPlayer(gameMap);
                if (weakest != null) {
                    lockedTarget = weakest;
                    try {
                        Attack.attackTarget(hero, lockedTarget, gameMap);
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi tấn công target: " + e.getMessage());
                    }
                    return;
                }

                // 6. (Có thể bổ sung: khám phá map hoặc nhặt vũ khí/support item tốt hơn nếu muốn)
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    // Phương thức hỗ trợ để tìm vật phẩm hồi máu phù hợp nhất dựa trên lostHp
    private static SupportItem findBestHealingItem(List<SupportItem> supportItems, float lostHp) {
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

        for (Element weapon : weapons) {
            if (InventoryManager.pickupable(hero, weapon)) {
                Node weaponNode = weapon.getPosition();
                if (!skipDarkArea && !checkInsideSafeArea(weaponNode, safeZone, mapSize)) continue;
                int dist = distance(currentPosition, weaponNode);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestWeapon = (Weapon) weapon;
                    nearestNode = weaponNode;
                }
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
            invManager.swapItem(gameMap, hero);
            System.out.println("Đang đứng trên vũ khí, thực hiện nhặt.");
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();

        int enemyRange = 3; // Nếu enemy có range = 1, thì sẽ tránh các 3x3 ô xung quanh nó
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
}