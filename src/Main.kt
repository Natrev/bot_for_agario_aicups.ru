import org.json.JSONArray
import org.json.JSONObject
import java.io.FileWriter
import java.util.*

val isDebug = false

val log: FileWriter? =
        if (isDebug)
            FileWriter("C:\\Users\\kruba\\IdeaProjects\\AgarIO\\out\\artifacts\\AgarIO_jar\\log.txt")
        else
            null

fun makeLog(msg: String) {
    if (log == null)
        return
    log!!.write("$msg\n")
    log!!.flush()
}

interface IVector{
    val length : Double
    val angle : Double
    val x : Double
    val y : Double

    operator fun plus(obj : IVector) : IVector
    operator fun minus(obj : IVector) : IVector

    operator fun times(scale : Double) : IVector
    operator fun times(vec : IVector) : Double



    operator fun compareTo(other : IVector) =
            if (x == other.x && y == other.y) 0
            else if (x < other.x || x == other.x && y < other.y) -1
            else 1
}

interface IPoint{
    val x : Double
    val y : Double

    operator fun plus(obj : IPoint) : IPoint
    operator fun minus(obj : IPoint) : IVector

    operator fun plus(obj : IVector) : IPoint
    operator fun minus(obj : IVector) : IPoint


    fun distance(obj : IPoint) : Double

    operator fun compareTo(other : IPoint) =
            if (x == other.x && y == other.y) 0
            else if (x < other.x || x == other.x && y < other.y) -1
            else 1
}

class DecartVector (override var x : Double, override var y : Double) : IVector{

    override val length : Double
        get() = Math.sqrt(x * x + y * y)

    override val angle : Double
        get() = Math.atan2(x, y)

    override fun plus(obj : IVector) = DecartVector(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IVector) = DecartVector(x = x - obj.x, y = y - obj.y)

    override fun times(scale : Double) = DecartVector(x = x * scale, y = y * scale)

    override fun times(vec : IVector) = x * vec.x + y * vec.y


    val normal : DecartVector
        get() = this * (1.0/Math.max(1.0, length))

    fun cos(vec : IVector) = (x * vec.x + y * vec.y) / Math.max(0.1, (length * vec.length))

    val perpen : DecartVector
        get() = DecartVector(y, -x)
}

class DecartPoint (override var x : Double, override var y : Double) : IPoint {

    override fun plus(obj : IPoint) = DecartPoint(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IPoint) = DecartVector(x = x - obj.x, y = y - obj.y)

    override fun plus(obj : IVector) = DecartPoint(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IVector) = DecartPoint(x = x - obj.x, y = y - obj.y)



    override fun distance(obj: IPoint): Double {
        val dx : Double = obj.x - x
        val dy : Double = obj.y - y
        return Math.sqrt(dx * dx + dy * dy)
    }

}

interface IRemember{
    var memory : Double

    val isForgotten : Boolean
        get() = memory  < 0

    fun refresh() : IRemember{
        memory = consts!!.GENERAL_MEMORY
        return this
    }

    fun pastTick() : IRemember{
        memory -= 1
        return this
    }
}


interface IPotential{
    var potential : Double


    fun refreshPotential(){
        potential = 0.0
    }
}


interface IPotentialSource{
    val force : Double
    val position : DecartPoint

    fun effect(player: Player) : DecartVector
}

abstract class Object(override var position : DecartPoint) : IRemember, IPotentialSource{
    override var memory = consts!!.GENERAL_MEMORY
    override val force = consts!!.GENERAL_FORCE
}


interface ICircle{
    val position : DecartPoint
    val radius: Double
    operator fun contains(point: DecartPoint) = radius > (point - position).length
}

abstract class CircularObject(val id : String, position : DecartPoint, var weight : Double) : Object(position)

abstract class Player(id : String, position: DecartPoint, weight : Double, var speed : DecartVector)
    : CircularObject(id, position, weight), ICircle{

    override val radius : Double
        get() = 2 * Math.sqrt(weight)

    fun maxSpeed() = consts!!.SPEED_FACTOR / weight

    fun splitDistance() = Math.abs(64 - maxSpeed() * maxSpeed())/(2 * consts!!.VISCOSITY)

    fun refresh(position: DecartPoint, weight : Double) : Player{
        if (memory + 1 == consts!!.GENERAL_MEMORY)
            speed = position - this.position
        else
            speed = DecartVector(0.0, 0.0)
        this.position = position
        this.weight = weight
        return this
    }

    fun isTo(pos : DecartPoint, vec : DecartVector) : Boolean{
        val dist = (position - pos).length
        val sin = radius * 1.5 / Math.max(1.0, dist)
        val cos = Math.sqrt(1.0 - sin * sin)
        return vec.cos(position - pos) > cos
    }


    fun getSeenCircle(cnt : Int) : SeenCircle {
        val sr =
                if (cnt == 1)
                    4 * radius
                else
                    2.5 * radius * cnt
        val cp =
                if (speed.length < 1.0)
                    position
                else
                    position + speed * (10.0 / speed.length)

        return SeenCircle(cp, sr)

    }

    override fun effect(player : Player) : DecartVector{
        val direction = player.speed
        val offset = position - player.position

        val cos = direction.cos(offset)
        val distance = offset.length

        if (1.2 * weight < player.weight && offset.length < (player.radius + radius) * 2)
            return offset.normal * (4 * (1 + cos) * force * weight / player.weight / distance)


        if (offset.length > player.getSeenCircle(2).radius || (1.2 * weight < player.weight))
            return DecartVector(0.0, 0.0)

        var vec =
                (if (speed.perpen.cos(player.speed) >= 0.0) speed.perpen
                else  speed.perpen * -1.0).normal + offset.normal * -1.0

        if (player.position.x < player.radius * 4 && DecartVector(1.0, 0.0).cos(vec) < 0){
            vec += DecartVector(1.0, 0.0) * (player.position.x / position.x)
        }

        if (player.position.y < player.radius * 4 && DecartVector(0.0, 1.0).cos(vec) < 0 ){
            vec += DecartVector(0.0, 1.0) * (player.position.y / position.y)
        }
        if (consts!!.GAME_WIDTH - player.position.x < player.radius * 4 && DecartVector(-1.0, 0.0).cos(vec) < 0 ){

            vec += DecartVector(-1.0, 0.0) * ((consts!!.GAME_WIDTH - player.position.x) / (consts!!.GAME_WIDTH - position.x))
        }
        if (consts!!.GAME_HEIGHT - player.position.y < player.radius * 4 && DecartVector(0.0, -1.0).cos(vec) < 0 ){

            vec += DecartVector(0.0, -1.0) * ((consts!!.GAME_HEIGHT - player.position.y) / (consts!!.GAME_HEIGHT - position.y))
        }

        return if (weight< player.weight * 1.15 ) vec * (force / player.weight / distance)
        else vec * (7  * force * weight / player.weight / distance)
    }
}

class Food(position : DecartPoint) : Object(position){
    override fun effect(player : Player) : DecartVector{
        val offset = position - player.position
        val direction = player.speed
        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))
        val distance = offset.length
        val rdp = world.getRadiusPerpen(player.position)
        val rdpcos = Math.abs(rdp.cos(position - player.position))



        return offset.normal * ((1.0 + cos)*force * consts!!.FOOD_MASS / player.weight  / distance)
    }
}

class Ejection(position: DecartPoint, val pId : String) : Object(position){
    override fun effect(player : Player) : DecartVector = TODO()
}

class Virus(id : String, position: DecartPoint, weight : Double) : CircularObject(id, position, weight){

    override fun effect(player : Player) : DecartVector{
        val offset = position - player.position
        val distance = offset.length
        if (distance > player.radius * 3 || player.weight < 120.0) return DecartVector(0.0, 0.0)
        return offset.normal * (-force / player.weight / (30.0 + Math.sqrt(offset.length)))
    }
}

class Enemy(id : String, val group : Int, position: DecartPoint, weight : Double, radius :Double, speed : DecartVector)
    : Player(id, position, weight, speed){


    override fun pastTick() : IRemember{
        memory -= 1
        position += speed
        return this
    }
}

class SeenCircle(override val position: DecartPoint, override val radius: Double) : ICircle

class Hero(id : String, position: DecartPoint, weight : Double, radius :Double, speed : DecartVector)
    : Player(id, position, weight, speed){

}


class HopeField(override val force : Double, val nearDistance : Double) : IPotentialSource {

    fun randomPosition() : DecartPoint
            = DecartPoint(x = (0.1 + 0.8 * Math.random()) * consts!!.GAME_WIDTH, y = (0.1 + 0.8 * Math.random()) * consts!!.GAME_HEIGHT)

    override var position = randomPosition()

    var t = 0.0

    override fun effect(player : Player) : DecartVector {
        val to_center = consts!!.CENTER - player.position
        val direction = player.speed

        var rdp = world.getRadiusPerpen(player.position)

        if (rdp.cos(direction) < 0) rdp *= -1.0
        return (rdp.normal + to_center.normal * 0.1 + direction.normal * 0.1)*(force / player.weight / (30.0 + Math.sqrt(to_center.length)))
    }

    fun check(where : DecartPoint){
        if ((position - where).length < nearDistance || t >= force) {
            position = randomPosition()
            t = 0.0
        }
    }
}

class AngleField(override val force : Double, override val position: DecartPoint, val nearDistance: Double ) : IPotentialSource{

    override fun effect(player : Player) : DecartVector{
        val offset = position - player.position
        val distance = offset.length
        if (distance > nearDistance) return DecartVector(0.0, 0.0)
        return offset.normal * (-force / player.weight / (30.0 + Math.sqrt(offset.length)))

    }
}

class BorderField(override val force : Double, override val position: DecartPoint, val nearDistance: Double ) : IPotentialSource{

    override fun effect(player : Player) : DecartVector{
        val offset = position - player.position
        val distance =
                if (position.y == -1.0) Math.abs(position.x - player.position.x)
                else Math.abs(position.y - player.position.y)

        if (distance > nearDistance) return DecartVector(0.0, 0.0)
        var vec =
                if (position.y == -1.0) DecartVector((player.position.x - position.x) / distance, 0.0)
                else DecartVector(0.0, (player.position.y - position.y) / distance)
        if (position.y == -1.0 && player.position.y < consts!!.GAME_HEIGHT - player.position.y)
            vec += DecartVector(0.0, 1.0) * ((consts!!.GAME_HEIGHT - player.position.y) / player.position.y)
        if (position.y == -1.0 && player.position.y > consts!!.GAME_HEIGHT - player.position.y)
            vec += DecartVector(0.0, -1.0) * (player.position.y / (consts!!.GAME_HEIGHT - player.position.y))
        if (position.x == -1.0 && player.position.x < consts!!.GAME_WIDTH - player.position.x)
            vec += DecartVector(1.0, 0.0) * ((consts!!.GAME_WIDTH - player.position.x) / player.position.x)
        if (position.x == -1.0 && player.position.x > consts!!.GAME_WIDTH - player.position.x)
            vec += DecartVector(-1.0, 0.0) * (player.position.y/(consts!!.GAME_WIDTH - player.position.x))
        return vec * (force / player.weight / (1 + offset.length))

    }
}


class PotentialObject(var toMove : DecartPoint) : IPotential{
    override var potential = 0.0

}



class PolarPotentialFields(var objects :List<Player>, val fields: List<IPotentialSource>, val points : List<PotentialObject>){
    fun toMove() : DecartPoint{


        points.forEach { it.refreshPotential() }

        for (obj in objects){

            var res = DecartVector(0.0, 0.0)
            for (field in fields){
                res += field.effect(obj)
            }
            for (point in points){
                point.potential += res.length * (point.toMove - obj.position).cos(res)

                if (point.toMove.y == 0.0 && obj.position.y < 2 * obj.radius)
                    point.potential -= 100 / (obj.position.y + 1)
                if (point.toMove.y == consts!!.GAME_HEIGHT && consts!!.GAME_HEIGHT - obj.position.y < 2 * obj.radius)
                    point.potential -= 100 / (consts!!.GAME_HEIGHT - obj.position.y + 1)
                if (point.toMove.x == 0.0 && obj.position.x < 2 * obj.radius)
                    point.potential -= 100 / (obj.position.x + 1)
                if (point.toMove.x == consts!!.GAME_WIDTH && consts!!.GAME_WIDTH - obj.position.x < 2 * obj.radius)
                    point.potential -= 100 / (consts!!.GAME_WIDTH - obj.position.x + 1)
            }
        }

        return points.maxBy { it.potential }!!.toMove
    }
}

class Consts(config : JSONObject){
    //game
    val SPEED_FACTOR  = config["SPEED_FACTOR"]!!.toString().toDouble()
    val GAME_WIDTH  = config["GAME_WIDTH"]!!.toString().toDouble()
    val GAME_HEIGHT  = config["GAME_HEIGHT"]!!.toString().toDouble()
    val FOOD_MASS = config["FOOD_MASS"]!!.toString().toDouble()
    val INERTION_FACTOR = config["INERTION_FACTOR"]!!.toString().toDouble()
    val VISCOSITY = config["VISCOSITY"]!!.toString().toDouble()
    val CENTER = DecartPoint(GAME_WIDTH/2, GAME_HEIGHT/2)

    // type
    val FOOD_TYPE = "F"
    val ENEMY_TYPE = "P"

    //custom
    val GENERAL_MEMORY = 100.0
    val GENERAL_FORCE = 100.0

}

class World(){
    var heroes = listOf<Hero>()
    var foods = listOf<Food>()
    var ejetions = listOf<Ejection>()
    var viruses = listOf<Virus>()
    var enemies = listOf<Enemy>()

    fun getRadiusPerpen(pos : DecartPoint) = (pos - consts!!.CENTER).perpen

    fun getPosition(obj : JSONObject) = DecartPoint(obj["X"]!!.toString().toDouble(), obj["Y"]!!.toString().toDouble())

    fun getSpeed(obj : JSONObject) = DecartVector(obj["SX"]!!.toString().toDouble(), obj["SY"]!!.toString().toDouble())

    fun updateFood(objects: JSONArray){

        val mp : SortedMap<DecartPoint, Food> = foods.map {Pair(it.position, it)} .toMap()
                .toSortedMap(object: Comparator<DecartPoint>{
                    override fun compare(c1: DecartPoint, c2: DecartPoint): Int = c1.compareTo(c2)
                })

        val seenFood = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == consts!!.FOOD_TYPE }.map {
            val pos = getPosition(it)
            if (pos in mp)
                mp[pos]!!.refresh() as Food
            else
                Food(position = pos)
        }
        foods = foods.filter { it.position !in mp }.map { it.pastTick() as Food}.filter { !it.isForgotten }
                .filter {
                    var ret = false
                    for (hero in heroes)
                        if (it.position in hero.getSeenCircle(heroes.size)){
                            ret = true
                            break
                        }
                    !ret
                } + seenFood
    }

    fun updateEnemies(objects: JSONArray){

        val mp = enemies.map {Pair(it.id, it)} .toMap().toSortedMap()
        val st = mutableSetOf<String>()

        val seenEnemies= objects.map { it as JSONObject}.filter { it["T"]!!.toString() == consts!!.ENEMY_TYPE }.map {
            val id = it["Id"]!!.toString()
            val pos = getPosition(it)
            val w = it["M"]!!.toString().toDouble()
            val r = it["R"]!!.toString().toDouble()
            if (id in mp) mp[id]!!.refresh(pos, w) as Enemy
            else Enemy(id = id, group = id[0].toInt() ,position = pos, weight = w, radius = r, speed=DecartVector(0.0,0.0))
        }

        for (seen in seenEnemies){
            st.add(seen.id)
        }

        enemies = enemies.filter { it.id !in st }.map { it.pastTick() as Enemy}.filter { !it.isForgotten }
                .filter {
                    var ret = false
                    for (hero in heroes)
                        if (it.position in hero.getSeenCircle(heroes.size)){
                            ret = true
                            break
                        }
                    !ret
                } + seenEnemies
    }


    fun update(mine : JSONArray, objects : JSONArray){
        heroes = mine.map { it as JSONObject}.map {
            val id = it["Id"]!!.toString()
            val pos = getPosition(it)
            val speed = getSpeed(it)
            val w = it["M"]!!.toString().toDouble()
            val r = it["R"]!!.toString().toDouble()
            Hero(id = id, position =  pos, weight = w, radius = r, speed = speed)
        }

        updateFood(objects)

        ejetions = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "E" }.map {
            Ejection(position = getPosition(it), pId = it["pId"]!!.toString())
        }
        viruses = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "V" }.map {
            Virus(id = it["Id"]!!.toString(), position = getPosition(it), weight = it["M"]!!.toString().toDouble())
        }

        updateEnemies(objects)
    }

    fun maxHeroSize() = heroes.map{ it.weight }.max()!!
    fun minHeroSize() = heroes.map{ it.weight }.min()!!

    fun maxEnemySize() = enemies.map{ it.weight }.max() ?: 0.0

    fun areSeenEnemy() = enemies.any{ it.memory == consts!!.GENERAL_MEMORY}
}

val world = World()

class StepInfo(private val toMove : DecartPoint, private val eject : Boolean = false, private val split : Boolean = false){
    val json : JSONObject
        get() = JSONObject(mapOf("X" to toMove.x, "Y" to toMove.y, "Split" to split, "Eject" to eject))
}

interface IStrategy{

    fun priority(world : World) : Double

    fun step(world : World) : StepInfo
}


class SimpleStrategy : IStrategy{
    override fun priority(world : World) = TODO(reason = "Стратегия пока одна")

    var world : World? = null
    val hopes = listOf(
            HopeField(force = 100.0, nearDistance = 100.0)
    )
    val borders = listOf(
            AngleField(force = 100.0, position = DecartPoint(x = 0.0, y = 0.0), nearDistance = 100.0),
            AngleField(force = 100.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = 0.0), nearDistance = 100.0),
            AngleField(force = 100.0, position = DecartPoint(x = 0.0, y = consts!!.GAME_HEIGHT), nearDistance = 100.0),
            AngleField(force = 100.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = consts!!.GAME_HEIGHT), nearDistance = 100.0),
            BorderField(force = 100.0, position = DecartPoint(x = 0.0, y = -1.0), nearDistance = 100.0),
            BorderField(force = 100.0, position = DecartPoint(x = -1.0, y = 0.0), nearDistance = 100.0),
            BorderField(force = 100.0, position = DecartPoint(x = -1.0, y = consts!!.GAME_HEIGHT), nearDistance = 100.0),
            BorderField(force = 100.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = -1.0), nearDistance = 100.0)
    )

    val toBorder : List<PotentialObject>
            get(){
                if (to_border == null){
                    to_border = (0..20).map { PotentialObject(DecartPoint(0.0, it * consts!!.GAME_HEIGHT/20.0))} +
                            (0..20).map { PotentialObject(DecartPoint(consts!!.GAME_WIDTH, it * consts!!.GAME_HEIGHT/20.0))} +
                            (1..19).map { PotentialObject(DecartPoint(it * consts!!.GAME_WIDTH/20.0, 0.0))} +
                            (1..19).map { PotentialObject(DecartPoint(it * consts!!.GAME_WIDTH/20.0, consts!!.GAME_HEIGHT))}
                }
                return to_border!!
            }

    var to_border : List<PotentialObject>? = null

    private fun findFood() : Food? = world!!.foods.firstOrNull()

    private fun potentialObjects() = toBorder //+
            //world!!.enemies.map { PotentialObject(it.position)} +
            //world!!.foods.filter{
             //           it.position.x > 50 && it.position.y > 50 && consts!!.GAME_WIDTH - it.position.x > 50 && consts!!.GAME_HEIGHT - it.position.y > 50
             //       }.take(10).map { PotentialObject(it.position)}

    var tick_to_spleet = 0

    override fun step(world : World) : StepInfo {

        tick_to_spleet -= 1

        this.world = world

        makeLog("center = { ${consts!!.CENTER.x}, ${consts!!.CENTER.y} }")
        makeLog("heroPos = { ${world.heroes[0].position.x}, ${world.heroes[0].position.y} }")

        var needSpleet = world.maxHeroSize() > 120.0 && world.maxEnemySize() * 2.4 < world.minHeroSize()
        var noNeedSpleet = tick_to_spleet > 0
        if (!needSpleet)
            for (hero in world.heroes) {
                if (needSpleet) break
                for (enemy in world.enemies) {
                    if (needSpleet) break
                    if (enemy.weight * 2.5 < hero.weight && hero.speed.length > 0.5 &&
                            hero.speed.cos(enemy.position - hero.position) > 0.96 &&
                            (enemy.position - hero.position).length < 1.2 * hero.splitDistance()
                    ) needSpleet = true
                    if (enemy.weight > hero.weight * 0.6 && hero.speed.length > 0.5 &&
                            hero.speed.cos(enemy.position - hero.position) > 0.96 &&
                            (enemy.position - hero.position).length < 1.2 * hero.splitDistance())
                        noNeedSpleet = true

                }
                for (virus in world.viruses) {
                    if (needSpleet) break
                    if (120.0 <= hero.weight && hero.speed.length > 0.5 &&
                            hero.speed.cos(virus.position - hero.position) > 0.96 &&
                            (virus.position - hero.position).length < hero.splitDistance())
                        noNeedSpleet = true

                }
            }

        needSpleet = !noNeedSpleet && needSpleet

        val fields = hopes + world.enemies + world.foods + borders + world.viruses

        val toMove = PolarPotentialFields(world.heroes, fields, potentialObjects()).toMove()
        if (needSpleet) tick_to_spleet = 50
        makeLog("toMove = { ${toMove.x}, ${toMove.y} }")
        makeLog("from = { ${world.heroes[0].position.x}, ${world.heroes[0].position.y} }")
        return StepInfo(toMove = toMove, split = needSpleet)
    }
}

var strata : SimpleStrategy? = null

var consts : Consts? = null

fun main(args: Array<String>) {
    val config = JSONObject(readLine())
    consts = Consts(config)
    strata = SimpleStrategy()
    while (true) {
        val tickData = JSONObject(readLine())
        val move = onTick(tickData)
        println(move)
    }
}

var tick = 0

fun onTick(tickData: JSONObject): JSONObject {
    tick += 1
    val mine = tickData.getJSONArray("Mine")!!
    val objects = tickData.getJSONArray("Objects")!!
    if (mine.length() != 0) {
        makeLog("tick[$tick]------------")
        world.update(mine, objects)
        return strata!!.step(world).json
    }
    return JSONObject(mapOf("X" to 0, "Y" to 0, "Debug" to "Died"))
}

fun findFood(objects: JSONArray): JSONObject? =
        objects.map { it as JSONObject }.firstOrNull { it["T"] == "F" }