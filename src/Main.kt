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

class DecartVector (override val x : Double, override val y : Double) : IVector{

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

    fun cos(vec : IVector) = (this * vec) / (length * vec.length)
}

class DecartPoint (override val x : Double, override val y : Double) : IPoint {

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

    operator fun plusAssign(field : IPotentialSource)
    operator fun timesAssign(field : IPotentialSource)

    fun refreshPotential(){
        potential = 0.0
    }
}


interface IPotentialSource{
    val force : Double
    val position : DecartPoint

    fun effect(where : DecartPoint, hero: Hero) : Double
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

abstract class Player(id : String, position: DecartPoint, weight : Double, var radius :Double, var speed : DecartVector)
    : CircularObject(id, position, weight){

    fun maxSpeed() = consts!!.SPEED_FACTOR / weight

    fun splitDistance() = Math.abs(64 - maxSpeed() * maxSpeed())/(2 * consts!!.VISCOSITY)

    fun refresh(position: DecartPoint, weight : Double, radius :Double) : Player{
        if (memory + 1 == consts!!.GENERAL_MEMORY)
            speed = position - this.position
        else
            speed = DecartVector(0.0, 0.0)
        this.position = position
        this.weight = weight
        return this
    }
}

class Food(position : DecartPoint) : Object(position){
    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val offset = position - where
        val direction = hero.speed
        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))
        val distance = (offset.length + (position - hero.position).length) / 2
        return force * consts!!.FOOD_MASS / hero.weight * (1.0 + cos) / distance
    }
}

class Ejection(position: DecartPoint, val pId : String) : Object(position){
    override fun effect(where : DecartPoint, hero: Hero) : Double = TODO()
}

class Virus(id : String, position: DecartPoint, weight : Double) : CircularObject(id, position, weight){

    override fun effect(where : DecartPoint, hero: Hero) : Double = TODO()
}

class Enemy(id : String, position: DecartPoint, weight : Double, radius :Double, speed : DecartVector)
    : Player(id, position, weight, radius, speed){


    override fun pastTick() : IRemember{
        memory -= 1
        position += speed
        return this
    }

    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val offset = position - where
        val direction = hero.speed

        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))
        val distance = (offset.length + (position - hero.position).length) / 2

        if (1.2 * weight < hero.weight)
            return 4 * (1 + cos) * force * weight / hero.weight / distance


        val offsetHero = position - hero.position
        val nextDirection = where - hero.position
        val cos2 = nextDirection.cos(offsetHero)
        val sin = Math.sqrt(1 - cos2 * cos2)

        return if (weight< hero.weight * 1.2 ) -force / hero.weight / distance
        else -10  * force * weight / hero.weight / distance
    }
}

class SeenCircle(override val position: DecartPoint, override val radius: Double) : ICircle

class Hero(id : String, position: DecartPoint, weight : Double, radius :Double, speed : DecartVector)
    : Player(id, position, weight, radius, speed){

    override fun effect(where : DecartPoint, hero: Hero) : Double = TODO()
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

}

fun getPosition(obj : JSONObject) = DecartPoint(obj["X"]!!.toString().toDouble(), obj["Y"]!!.toString().toDouble())

fun getSpeed(obj : JSONObject) = DecartVector(obj["SX"]!!.toString().toDouble(), obj["SY"]!!.toString().toDouble())


var GAME_WIDTH = 0.0
var GAME_HEIGHT = 0.0




class HopeField(override val force : Double, val nearDistance : Double) : IPotentialSource {

    fun randomPosition() : DecartPoint
            = DecartPoint(x = (0.1 + 0.8 * Math.random()) * consts!!.GAME_WIDTH, y = (0.1 + 0.8 * Math.random()) * consts!!.GAME_HEIGHT)

    override var position = randomPosition()

    var t = 0.0

    override fun effect(where: DecartPoint, hero: Hero): Double {
        val offset = position - where
        val direction = hero.speed
        //makeLog("\t\t HopeField effect")
        //makeLog("\t\t where = {${where.x}, ${where.y}}")
        //makeLog("\t\t direction = {${direction.x}, ${direction.y}}")
        //makeLog("\t\t offset = {${offset.x}, ${offset.y}}")
        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))
        //makeLog("\t\t cos = $cos")
        //makeLog("\t\t (force * (0.5) / offset.length = ${force * (0.5) / offset.length}}")
        val distance = (offset.length + (position - hero.position).length) / 2
        return (force )/ hero.weight * (1.0 + cos + 0.2 * Math.random()) / Math.sqrt(distance)
    }

    fun check(where : DecartPoint){
        if ((position - where).length < nearDistance || t >= force) {
            position = randomPosition()
            t = 0.0
        }
    }
}

class AngleField(override val force : Double, override val position: DecartPoint, val nearDistance: Double ) : IPotentialSource{

    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val distance = Math.abs(position.x - where.x) + Math.abs(position.y - where.y)
        if (distance > nearDistance) return 0.0
        return -force * force / hero.weight / distance

    }
}


class PotentialObject(var toMove : DecartPoint, val heroes : List<Hero>, val ch : Double = 0.0) : IPotential{
    override var potential = 0.0

    override fun plusAssign(field: IPotentialSource){
        potential += heroes.map {
                val dir = (toMove - it.position).normal * consts!!.SPEED_FACTOR
            field.effect(it.position + dir, it)
        }.sum() + ch
    }

    override fun timesAssign(field: IPotentialSource) {
        potential *= heroes.map {
            val vec = (toMove - it.position)
            if (vec.length < it.maxSpeed()) field.effect(toMove, it)
            else{
                val dir = (toMove - it.position).normal * it.maxSpeed()
                field.effect(it.position + dir, it)
            }
        }.sum() + ch
    }
}

class PolarPotentialFields(val objects :List<PotentialObject>, val fields: List<IPotentialSource>){
    fun toMove() : DecartPoint{


        for (obj in objects) {
            obj.refreshPotential()
        }

        for (obj in objects){
            for (field in fields){
                obj += field
            }
        }

        return objects.maxBy { it.potential }!!.toMove
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
            if (id in mp) mp[id]!!.refresh(pos, w, r) as Enemy
            else Enemy(id = id, position = pos, weight = w, radius = r, speed=DecartVector(0.0,0.0))
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
            HopeField(force = 70.0, nearDistance = 100.0)
    )
    val borders = listOf(
            AngleField(force = 200.0, position = DecartPoint(x = 0.0, y = 0.0), nearDistance = 100.0),
            AngleField(force = 200.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = 0.0), nearDistance = 100.0),
            AngleField(force = 200.0, position = DecartPoint(x = 0.0, y = consts!!.GAME_HEIGHT), nearDistance = 100.0),
            AngleField(force = 200.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = consts!!.GAME_HEIGHT), nearDistance = 100.0)
    )

    val toBorder : List<PotentialObject>
            get(){
                if (to_border == null){
                    to_border = (0..10).map { PotentialObject(DecartPoint(0.0, it * consts!!.GAME_HEIGHT/10.0), world!!.heroes, -1.0)} +
                            (0..10).map { PotentialObject(DecartPoint(consts!!.GAME_WIDTH, it * consts!!.GAME_HEIGHT/10.0), world!!.heroes, -1.0)} +
                            (1..9).map { PotentialObject(DecartPoint(0.0, 0.0), world!!.heroes, -1.0)} +
                            (1..9).map { PotentialObject(DecartPoint(it * consts!!.GAME_WIDTH/10.0, consts!!.GAME_HEIGHT), world!!.heroes, -1.0)}
                }
                return to_border!!
            }

    var to_border : List<PotentialObject>? = null

    private fun findFood() : Food? = world!!.foods.firstOrNull()

    private fun potentialObjects() =
            world!!.enemies.map { PotentialObject(it.position, world!!.heroes)} +
                    world!!.foods.take(10).map { PotentialObject(it.position, world!!.heroes)} +
                    hopes.map { PotentialObject(it.position, world!!.heroes)} + toBorder

    override fun step(world : World) : StepInfo {
        this.world = world

        var needSpleet = world.maxHeroSize() > 120.0 && world.maxEnemySize() * 2.4 < world.minHeroSize()

        if (!needSpleet)
            for (hero in world.heroes) {
                if (needSpleet) break
                for (enemy in world.enemies) {
                    if (needSpleet) break
                    if (enemy.weight * 2.5 < hero.weight && hero.speed.length > 0.5 &&
                            hero.speed.cos(enemy.position - hero.position) > 0.96 &&
                            (enemy.position - hero.position).length < 1.2 * hero.splitDistance()
                    ) needSpleet = true

                }
            }

        for (hope in hopes)
            for (hero in world.heroes)
                hope.check(hero.position)

        val fields = world.foods + world.enemies + borders + hopes

        val toMove = PolarPotentialFields(potentialObjects(), fields).toMove()
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