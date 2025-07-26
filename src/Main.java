import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.base.Node;
import jsclub.codefest.sdk.model.Element;
import jsclub.codefest.sdk.model.ElementType;
import jsclub.codefest.sdk.model.GameMap;
import jsclub.codefest.sdk.Hero;
import jsclub.codefest.sdk.model.npcs.Enemy;
import jsclub.codefest.sdk.model.obstacles.Obstacle;
import jsclub.codefest.sdk.model.obstacles.ObstacleTag;
import jsclub.codefest.sdk.model.players.Player;
import jsclub.codefest.sdk.model.support_items.SupportItem;
import jsclub.codefest.sdk.model.weapon.Weapon;
import java.io.IOException;
import java.util.*;

import static jsclub.codefest.sdk.algorithm.PathUtils.*;

public class Main {
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "111091";
    private static final String PLAYER_NAME = "botable";
    private static final String SECRET_KEY = "sk-BbXSmc7WSxisry8MS8RkHg:Z7xhZs3QIEF5iYHB-cRIFP11v5aS_6aMJd_DN-DDhk2lLmPw9JY6O9UzDwo7CPfDVf5KYwXWJ4rZxsASq0QXmw";

    static Node lastChestPosition = null;
    static Obstacle lastChest = null;
    static Player lockedTarget = null;
    static final float maxHealth = 100.0f; // Máu tối đa khởi đầu của player
    static int lootRadius = 2; // Bán kính loot đồ xung quanh player
    private static int lastRetreatStep = -100; // Lưu bước cuối cùng đã thực hiện retreat
    static EnemyTrajectoryCollector collector = new EnemyTrajectoryCollector();
    static String lastPath = null; // Lưu đường đi gần nhất
    static boolean isFollowingAlly = false; // Biến để theo dõi trạng thái bám theo ally
    static Node retreatTarget = null;
    static int lastDensityCheck = 0;
    static int mapTime = 1200; // Thời gian của bản đồ: n step -> n/2 s

    public static void main(String[] args) throws IOException {
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);

        Emitter.Listener onMapUpdate = new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                GameMap gameMap = hero.getGameMap();
                gameMap.updateOnUpdateMap(args[0]);

                collector.collect(gameMap, gameMap.getStepNumber());

                float currentHealth = gameMap.getCurrentPlayer().getHealth();
                System.out.println("========== Step: " + gameMap.getStepNumber() + " ==========");

                // Kiểm tra xem có đang ở ngoài vùng an toàn không
                Node currentPosition = gameMap.getCurrentPlayer().getPosition();
                int safeZone = gameMap.getSafeZone();
                int mapSize = gameMap.getMapSize();

                if (!checkInsideSafeArea(currentPosition, safeZone, mapSize)) {
                    try {
                        MapManager.moveToSafePoint(hero, gameMap);
                        return;
                    } catch (IOException | InterruptedException e) {
                        System.out.println("Lỗi khi di chuyển vào vùng an toàn: " + e.getMessage());
                    }
                }

                // Phân tích area density định kỳ (mỗi 15 tick)
                if (gameMap.getStepNumber() - lastDensityCheck >= 15) {
                    analyzeAndUpdateStrategy(gameMap, currentHealth);
                    lastDensityCheck = gameMap.getStepNumber();
                }

                // 10 giây đầu nhặt item ở gần
                if (gameMap.getStepNumber() < 20) {
                    Obstacle nearChest = ItemManager.checkIfHasChest(gameMap, 5);
                    if (nearChest != null) {
                        lastChestPosition = new Node(nearChest.getX(), nearChest.getY());
                        lastChest = nearChest;
                        try {
                            ItemManager.openChest(gameMap, hero, nearChest);
                        } catch (IOException e) {
                            System.out.println("Lỗi khi mở rương: " + e.getMessage());
                        }
                        return;
                    }
                    lootRadius = 5;
                    ItemManager.lootNearbyItems(hero, gameMap, lootRadius);
                }

                // Có người chơi ở gần thì bem luôn
                if (gameMap.getStepNumber() < 20) {
                    // Nếu có vũ khí thì bem player trong bán kính 3
                    if (InventoryManager.checkIfHasWeapon(hero)) {
                        Player nearByPlayer = Attack.checkIfHasNearbyPlayer(gameMap, 3);
                        if (nearByPlayer != null) {
                            lockedTarget = nearByPlayer;
                            System.out.println("Đã tìm thấy người chơi gần nhất: " + nearByPlayer.getId() + ", máu: " + nearByPlayer.getHealth());
                        } else {
                            System.out.println("Không có người chơi gần nào.");
                        }
                    }
                    // Nếu không có vũ khí thì bem player trong bán kính 2
                    else {
                        Player nearByPlayer = Attack.checkIfHasNearbyPlayer(gameMap, 2);
                        if (nearByPlayer != null) {
                            lockedTarget = nearByPlayer;
                            System.out.println("Đã tìm thấy người chơi gần nhất: " + nearByPlayer.getId() + ", máu: " + nearByPlayer.getHealth());
                        } else {
                            System.out.println("Không có người chơi gần nào.");
                        }
                    }
                }

                // Ưu tiên 1: Sử dụng item hỗ trợ đặc biệt nếu có
                Health.useSpecialSupportItem(gameMap, hero);

                // Ưu tiên 2: Nếu đang trong chế độ retreat
                System.out.println("Health: " + currentHealth + ". Retreat: " + (retreatTarget != null ? retreatTarget : "Không"));
                if (retreatTarget != null) {
                    if (executeRetreat(gameMap, hero)) {
                        return; // Đã di chuyển, không làm gì khác
                    }
                }

                System.out.println("Số người chơi khác: " + gameMap.getOtherPlayerInfo().size());
                System.out.println("Locked target: " + (lockedTarget != null ? lockedTarget.getId() + " (HP: " + lockedTarget.getHealth() + ")" : "null"));

                // Ưu tiên 3: Nếu máu thấp và ở khu vực không đông người, ưu tiên sử dụng Unicorn Blood
                if (currentHealth <= maxHealth * 0.25f && hero.getInventory().getListSupportItem().stream().anyMatch(item -> item.getId().equals("UNICORN_BLOOD"))) {
                    try {
                        hero.useItem("UNICORN_BLOOD");
                        return;
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                // Nếu đang bám theo ally ở cuối game và có người chơi trong bán kính 3 thì tấn công
                if (isFollowingAlly) {
                    Player nearByPlayer = Attack.checkIfHasNearbyPlayer(gameMap, 3);
                    if (nearByPlayer != null) {
                        System.out.println("Đã tìm thấy người chơi gần nhất: " + nearByPlayer.getId() + ", máu: " + nearByPlayer.getHealth());
                        movementSet(gameMap, hero, nearByPlayer, currentHealth);
                    }
                }

                // 45 giây cuối
                List<SupportItem> supportItems = hero.getInventory().getListSupportItem();
                if (gameMap.getStepNumber() >= mapTime - 90 && gameMap.getStepNumber() <= mapTime) {
                    // Nếu trong người có support item thì tấn công người chơi yếu máu hơn gần nhất
                    if (!supportItems.isEmpty()) {
                        Player weakestPlayer = Attack.findWeakestPlayer(gameMap, 7, currentPosition);
                        if (weakestPlayer != null) {
                            lockedTarget = weakestPlayer;
                            System.out.println("Đang tấn công người chơi yếu máu nhất: " + weakestPlayer.getId() + ", máu: " + weakestPlayer.getHealth());
                            movementSet(gameMap, hero, weakestPlayer, currentHealth);
                            return; // Đã tấn công thành công
                        } else {
                            System.out.println("Không tìm thấy người chơi yếu máu trong bán kính 7, bám theo ally");
                            Node nearestAlly = Health.findNearestAlly(gameMap);
                            if (nearestAlly != null) {
                                System.out.println("Đang bám theo ally: " + nearestAlly);
                                Health.moveToAlly(gameMap, nearestAlly, hero);
                                isFollowingAlly = true; // Đánh dấu là đang bám theo ally
                            } else {
                                System.out.println("Không tìm thấy ally gần nhất.");
                            }
                        }
                    }
                    // Nếu trong người không có support item thì bám theo ally
                    else {
                        Node nearestAlly = Health.findNearestAlly(gameMap);
                        if (nearestAlly != null) {
                            System.out.println("Đang bám theo ally: " + nearestAlly);
                            Health.moveToAlly(gameMap, nearestAlly, hero);
                            isFollowingAlly = true; // Đánh dấu là đang bám theo ally
                        } else {
                            System.out.println("Không tìm thấy ally gần nhất.");
                        }
                    }
                }

                if (currentHealth <= maxHealth * 0.6 && (lockedTarget == null || lockedTarget.getHealth() >= currentHealth)) {
                    if (Health.healByAlly(gameMap, hero, 5)) { // Ally ở gần thì chạy tới, xa thì dùng suppportitem luôn
                        return;
                    } else {
                        Element healingItem = Health.findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
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
                }

                // Ưu tiên hồi máu nếu máu dưới 80% và không có locked target hoặc locked target máu cao hơn mình
                if (currentHealth <= maxHealth * 0.8f) {
                    Element healingItem = Health.findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
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

                // Ưu tiên mở trứng nếu có trứng bất kể vị trí
                Obstacle targetEgg = ItemManager.hasEgg(gameMap, 15);
                if (targetEgg != null && (lockedTarget == null || lockedTarget.getHealth() >= currentHealth) &&
                        checkInsideSafeArea(targetEgg.getPosition(), safeZone, mapSize)) {
                    System.out.println("Đã tìm thấy trứng: " + targetEgg.getId() + ", vị trí: " + targetEgg.getPosition());
                    lastChestPosition = targetEgg.getPosition();
                    lastChest = targetEgg;
                    try {
                        ItemManager.openChest(gameMap, hero, targetEgg);
                        return;
                    } catch (IOException e) {
                        System.out.println("Lỗi khi phá trứng: " + e.getMessage());
                    }
                }

                // Nếu vừa phá xong rương, còn item quanh rương thì nhặt
                if (lastChestPosition != null) {
                    lootRadius = 5;
                    if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                        return; // Ưu tiên nhặt item quanh rương, xong mới làm việc khác
                    } else {
                        lastChestPosition = null; // Không còn item quanh, reset
                    }
                }

                // Trên đường đi nếu dẫm phải item thì cứ swap
                Element element = gameMap.getElementByIndex(gameMap.getCurrentPlayer().getX(), gameMap.getCurrentPlayer().getY());
                if ((element instanceof Weapon || element instanceof SupportItem) && ItemManager.pickupable(hero, element)) {
                    ItemManager.swapItem(gameMap, hero);
                }

                // Nếu vũ khí yếu, không có locked target hoặc locked target máu cao hơn mình, ưu tiên nhặt vũ khí
                if (Attack.currentDamage(hero) < 10 && (lockedTarget == null || lockedTarget.getHealth() >= currentHealth)) {
                    System.out.println("Không có vũ khí hoặc locked target nhiều máu hơn, ưu tiên nhặt vũ khí.");
                    lootRadius = 5;
                    if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                        return; // Ưu tiên nhặt item quanh
                    }
                }

                if (!Attack.isCombatReady(hero)) {
                    if (lockedTarget == null || distance(currentPosition, lockedTarget.getPosition()) > 5) {
                        System.out.println("Vũ khí yếu hoặc locked target ở xa, ưu tiên nhặt vũ khí.");
                        Obstacle nearChest = ItemManager.checkIfHasChest(gameMap, 5);
                        if (nearChest != null) {
                            lastChestPosition = new Node(nearChest.getX(), nearChest.getY());
                            lastChest = nearChest;
                            try {
                                ItemManager.openChest(gameMap, hero, nearChest);
                            } catch (IOException e) {
                                System.out.println("Lỗi khi mở rương: " + e.getMessage());
                            }
                            return;
                        }
                        lootRadius = 5;
                        ItemManager.lootNearbyItems(hero, gameMap, lootRadius);
                    } else {
                        System.out.println("Không có vũ khí nhưng đủ điều kiện tấn công locked target.");
                    }
                }

                // Ưu tiên loot item tốt hơn xung quanh nếu có
                lootRadius = 3;
                if (ItemManager.lootNearbyItems(hero, gameMap, lootRadius)) {
                    return;
                }

                // Ưu tiên tấn công locked target
                updateLockedTarget(gameMap, hero);
                if (lockedTarget != null) {
                    // Kiểm tra xem locked target có còn sống không
                    Player current = null;
                    for (Player p : gameMap.getOtherPlayerInfo()) {
                        if (p.getId().equals(lockedTarget.getId())) {
                            current = p;
                            break;
                        }
                    }

                    if (current == null || current.getHealth() <= 0) {
                        lockedTarget = null;
                    } else {
                        movementSet(gameMap, hero, current, currentHealth);
                        return;
                    }
                }

                // Nếu có 2 players đấm nhau ở gần, đợi gần chết nhảy vào bú kill
                List<Player> finishTargets = MapManager.detectCombatAndFinishTargets(gameMap, 25, 2);
                Node myPos = gameMap.getCurrentPlayer().getPosition();
                int attackRangeToBot = 4; // Ví dụ chỉ tấn công nếu giao tranh cách bot <= 4 ô

                for (Player target : finishTargets) {
                    if (distance(myPos, target.getPosition()) <= attackRangeToBot) {
                        lockedTarget = target;
                        movementSet(gameMap, hero, lockedTarget, currentHealth);
                    }
                }

                // Ưu tiên tấn công kẻ địch ở gần
                Player player = Attack.checkIfHasNearbyPlayer(gameMap, 3);
                if (player != null) {
                    lockedTarget = player;
                    movementSet(gameMap, hero, player, currentHealth);
                    return;
                }

                // Ưu tiên mở rương nếu có rương trong phạm vi
                Obstacle targetChest = ItemManager.checkIfHasChest(gameMap, 5);
                if (targetChest != null) {
                    lastChestPosition = new Node(targetChest.getX(), targetChest.getY());
                    lastChest = targetChest;
                    try {
                        ItemManager.openChest(gameMap, hero, targetChest);
                    } catch (IOException e) {
                        System.out.println("Lỗi khi mở rương: " + e.getMessage());
                    }
                    return;
                }

                // Ưu tiên tấn công người chơi yếu nhất (trong range 10) hoặc gần nhất
                Player weakest = Attack.findWeakestPlayer(gameMap, 10, currentPosition);
                if (weakest != null) {
                    lockedTarget = weakest;
                    movementSet(gameMap, hero, weakest, currentHealth);
                    return;
                }

                // Nếu không có ai yếu nhất (tức là tất cả máu bằng nhau), chọn người gần nhất
                Player nearest = Attack.findNearestPlayer(gameMap, gameMap.getCurrentPlayer().getPosition());
                if (nearest != null) {
                    lockedTarget = nearest;
                    movementSet(gameMap, hero, nearest, currentHealth);
                    return;
                }

                // (Có thể bổ sung: khám phá map hoặc nhặt vũ khí/support item tốt hơn nếu muốn)
            }
        };

        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }

    public static void movementSet(GameMap gameMap, Hero hero, Player target, float currentHealth) {

        try {
            boolean attacked = Attack.attackTarget(hero, target, gameMap);
            if (attacked) {
                System.out.println("Đã tấn công target: " + target.getId() + ", máu còn: " + target.getHealth());
                return; // Nếu đã tấn công thành công, không cần làm gì khác
            }

            SupportItem healingItem = Health.findBestHealingItem(hero.getInventory().getListSupportItem(), maxHealth - currentHealth);
            if (currentHealth <= maxHealth * 0.8f && healingItem != null) {
                hero.useItem(healingItem.getId());
                return;
            }

            Node currentPos = hero.getGameMap().getCurrentPlayer().getPosition();
            Node targetPos = target.getPosition();
            if (currentPos == null || targetPos == null) {
                System.out.println("currentPos or targetPos is null, cannot proceed.");
                return;
            }
            String dir = getDirection(currentPos, targetPos);

            Weapon throwable = hero.getInventory().getThrowable();
            boolean hasThrowable = (throwable != null);

            boolean inThrowableRange = hasThrowable && Attack.isInsideRange(gameMap, throwable, currentPos, targetPos, dir);

            // Nếu có throwable và mục tiêu đang trong tầm, thì đứng lại và không tiến tới nữa
            if (inThrowableRange) {
                System.out.println("Mục tiêu trong tầm throwable, đứng lại để dùng throwable.");
                return;
            }

            // Nếu không có throwable hoặc mục tiêu ngoài tầm, thì tiếp tục di chuyển tới
            System.out.println("Mục tiêu ngoài tầm throwable hoặc không có throwable, tiến tới.");
            moveToTarget(hero, target, gameMap, true);

        } catch (IOException | InterruptedException e) {
            System.out.println("Lỗi khi tấn công target (người gần nhất): " + e.getMessage());
        }
    }

    public static List<Node> getRestrictedNodes(GameMap gameMap) {
        List<Node> restrictedNodes = new ArrayList<>();
        int enemyRange = 1;
        int futureTickCount = 2;

        // Logic cũ cho enemy và obstacles (giữ nguyên)
        for (Enemy enemy : gameMap.getListEnemies()) {
            if (enemy.getPosition() == null) continue;

            Node spawnPos = EnemyTrajectoryCollector.enemySpawnPos.get(enemy);
            if (spawnPos == null) spawnPos = enemy.getPosition();

            if (collector.isReady()) {
                EnemyTrajectoryCollector.TrajectoryInfo info = collector.getTrajectory(enemy.getId(), spawnPos);
                if (info != null) {
                    for (int future = 1; future <= futureTickCount; future++) {
                        Node dangerPos = info.getPositionAtTick(gameMap.getStepNumber() + future - 1);
                        for (int dx = -enemyRange; dx <= enemyRange; dx++) {
                            for (int dy = -enemyRange; dy <= enemyRange; dy++) {
                                restrictedNodes.add(new Node(dangerPos.getX() + dx, dangerPos.getY() + dy));
                            }
                        }
                    }
                }
            } else {
                int cautiousRange = 2;
                Node dangerPos = enemy.getPosition();
                for (int dx = -cautiousRange; dx <= cautiousRange; dx++) {
                    for (int dy = -cautiousRange; dy <= cautiousRange; dy++) {
                        restrictedNodes.add(new Node(dangerPos.getX() + dx, dangerPos.getY() + dy));
                    }
                }
            }
        }

        // Tránh chướng ngại vật
        for (Obstacle obstacle : gameMap.getListObstacles()) {
            if (obstacle.getPosition() != null && (obstacle.getTags().contains(ObstacleTag.TRAP) || !obstacle.getTags().contains(ObstacleTag.CAN_GO_THROUGH))) {
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

    // Di chuyển đến mục tiêu - Fixed version
    public static void moveToTarget(Hero hero, Node targetNode, GameMap gameMap, boolean skipDarkArea) throws IOException, InterruptedException {
        if (targetNode == null) {
            System.out.println("Target Node không hợp lệ.");
            return;
        }

        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        int safeZone = gameMap.getSafeZone();
        int mapSize = gameMap.getMapSize();

        // Tìm đường đi ngắn nhất
        List<Node> restrictedNodes = Main.getRestrictedNodes(gameMap);
        restrictedNodes.remove(targetNode);
        String path;

        if (gameMap.getElementByIndex(targetNode.x, targetNode.y).getType().equals(ElementType.PLAYER)) {
            path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, false);
        } else {
            path = getShortestPath(gameMap, restrictedNodes, currentPosition, targetNode, skipDarkArea);
        }

        if (path == null || path.isEmpty()) {
            System.out.println("Không tìm thấy đường đi đến mục tiêu!");

            // Kiểm tra fallback với lastPath
            if (checkInsideSafeArea(targetNode, safeZone, mapSize) && lastPath != null && !lastPath.isEmpty()) {
                // Lấy bước tiếp theo từ lastPath
                String nextStep = lastPath.substring(0, 1);
                Node nextPos = getNextPosition(currentPosition, nextStep);

                // KIỂM TRA KỸ CÀNG: nextPos phải hợp lệ và không phải obstacle
                if (nextPos != null &&
                        !restrictedNodes.contains(nextPos) &&
                        isValidPosition(nextPos, mapSize) &&
                        checkInsideSafeArea(nextPos, safeZone, mapSize)) {

                    path = nextStep; // Chỉ lấy 1 bước tiếp theo, không phải toàn bộ lastPath
                    System.out.println("Sử dụng fallback path: " + nextStep);
                } else {
                    System.out.println("LastPath không hợp lệ, reset target");
                    Main.lockedTarget = null;
                    lastPath = null;
                    path = null;
                }
            } else {
                Main.lockedTarget = null;
                lastPath = null;
                path = null;
            }
        } else {
            lastPath = path; // Chỉ lưu lastPath khi tìm thấy đường đi hợp lệ
        }

        if (path == null) {
            System.out.println("Không có đường đi hợp lệ, dừng di chuyển.");
            return;
        }

        // Kiểm tra bước tiếp theo
        String step = path.substring(0, 1);
        Node positionAfterStep = getNextPosition(currentPosition, step);

        if (positionAfterStep == null) {
            System.out.println("Invalid move direction: " + step);
            return;
        }

        // Kiểm tra xem bước tiếp theo có phải obstacle không
        if (restrictedNodes.contains(positionAfterStep)) {
            System.out.println("Cannot move to obstacle at: " + positionAfterStep);
            Main.lockedTarget = null;
            lastPath = null;
            return;
        }

        // Kiểm tra safe zone
        if (!checkInsideSafeArea(positionAfterStep, safeZone, mapSize)) {
            System.out.println("Cannot move outside safe zone: " + positionAfterStep);

            Element targetElement = gameMap.getElementByIndex(targetNode.x, targetNode.y);
            if (lockedTarget != null && lockedTarget.equals(targetElement)) {
                Weapon gun = hero.getInventory().getGun();
                Weapon melee = hero.getInventory().getMelee();
                Weapon throwable = hero.getInventory().getThrowable();

                if (((gun == null || !Attack.isInsideRange(gameMap, gun, currentPosition, targetNode, getDirection(currentPosition, targetNode))) &&
                        !Attack.isInsideRange(gameMap, melee, currentPosition, targetNode, getDirection(currentPosition, targetNode))) &&
                        (throwable == null || !Attack.isInsideRange(gameMap, throwable, currentPosition, targetNode, getDirection(currentPosition, targetNode)))) {
                    Main.lockedTarget = null; // Reset target if cannot attack
                }

                movementSet(gameMap, hero, Main.lockedTarget, hero.getGameMap().getCurrentPlayer().getHealth());
            } else {
                System.out.println("Cannot attack locked target: " + (lockedTarget != null ? lockedTarget.getId() : "null"));
            }
            return;
        }

        hero.move(step);
        System.out.println("Moved 1 step " + step + " towards target: " + gameMap.getElementByIndex(targetNode.x, targetNode.y).getId());
    }

    // Helper method để tính toán vị trí tiếp theo
    private static Node getNextPosition(Node currentPosition, String step) {
        return switch (step) {
            case "u" -> new Node(currentPosition.getX(), currentPosition.getY() + 1);
            case "d" -> new Node(currentPosition.getX(), currentPosition.getY() - 1);
            case "l" -> new Node(currentPosition.getX() - 1, currentPosition.getY());
            case "r" -> new Node(currentPosition.getX() + 1, currentPosition.getY());
            default -> null;
        };
    }

    // Helper method để kiểm tra vị trí có hợp lệ không
    private static boolean isValidPosition(Node position, int mapSize) {
        return position.getX() >= 0 && position.getX() < mapSize &&
                position.getY() >= 0 && position.getY() < mapSize;
    }

    // Thực hiện retreat
    private static boolean executeRetreat(GameMap gameMap, Hero hero) {
        if (retreatTarget == null) return false;

        Node currentPos = gameMap.getCurrentPlayer().getPosition();
        if (distance(currentPos, retreatTarget) < 5) {
            System.out.println("Đã tới khu vực an toàn, thoát chế độ retreat");
            retreatTarget = null;
            return false;
        }

        // Tìm đường đi tới retreat target
        List<Node> restrictedNodes = getRestrictedNodes(gameMap); // true = avoid crowded areas
        String path = getShortestPath(gameMap, restrictedNodes, currentPos, retreatTarget, true);

        if (path != null && path.length() > 1) {
            String step = path.substring(0, 1);
            try {
                hero.move(step);
                System.out.println("Retreat move: " + step);
                return true;
            } catch (Exception e) {
                System.out.println("Lỗi khi retreat: " + e.getMessage());
            }
        }

        return false;
    }

    // Update lockedTarget based on nearby players and current state
    public static void updateLockedTarget(GameMap gameMap, Hero hero) {
        Node currentPosition = gameMap.getCurrentPlayer().getPosition();
        Weapon gun = hero.getInventory().getGun();
        Weapon throwable = hero.getInventory().getThrowable();

        int checkRadius = 3;
        if (throwable != null && gun != null) {
            checkRadius = Math.max(throwable.getRange()[1], gun.getRange()[1]);
        } else if (throwable != null) {
            checkRadius = throwable.getRange()[1];
        } else if (gun != null) {
            checkRadius = gun.getRange()[1];
        }

        Player nearbyPlayer = Attack.checkIfHasNearbyPlayer(gameMap, checkRadius);

        // If lockedTarget is dead or null, update to nearbyPlayer
        if (lockedTarget == null || lockedTarget.getHealth() <= 0) {
            lockedTarget = nearbyPlayer;
            return;
        }

        // If a new nearby player is weaker or closer, update lockedTarget
        if (nearbyPlayer != null && !lockedTarget.equals(nearbyPlayer)) {
            if (lockedTarget.getHealth() > nearbyPlayer.getHealth()) {
                lockedTarget = nearbyPlayer;
            } else if (lockedTarget.getHealth().equals(nearbyPlayer.getHealth())) {
                Node lockedPos = lockedTarget.getPosition();
                Node nearPos = nearbyPlayer.getPosition();
                if (distance(currentPosition, lockedPos) > distance(currentPosition, nearPos)) {
                    lockedTarget = nearbyPlayer;
                }
            }
        }
    }

    private static void analyzeAndUpdateStrategy(GameMap gameMap, float currentHealth) {
        boolean isCurrentAreaCrowded = MapManager.isCurrentAreaCrowded(gameMap, 1);

        // Add cooldown: only allow retreat every 10 steps
        if (currentHealth <= maxHealth * 0.25f && isCurrentAreaCrowded &&
            (lockedTarget == null || lockedTarget.getHealth() > currentHealth) &&
            gameMap.getStepNumber() - lastRetreatStep > 10) {
            retreatTarget = MapManager.findSafestNearbyArea(gameMap, currentHealth);
            lastRetreatStep = gameMap.getStepNumber();
            System.out.println("RETREAT MODE: Low health + crowded area");
        } else if (retreatTarget != null) {
            // If already retreating, check if area is still safe
            if (!MapManager.isCurrentAreaCrowded(gameMap, 1) || currentHealth > maxHealth * 0.5f) {
                System.out.println("Safe or healed, exit retreat mode");
                retreatTarget = null;
            }
        }
    }
}