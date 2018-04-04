import org.json.JSONArray
import org.json.JSONObject
import java.io.FileWriter
import java.util.logging.Logger

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

interface IVector<T>{
    val length : T
    val angle : T
    val x : T
    val y : T

    operator fun plus(obj : IVector<T>) : IVector<T>
    operator fun minus(obj : IVector<T>) : IVector<T>

    operator fun times(scale : T) : IVector<T>
    operator fun times(vec : IVector<T>) : T
}

interface IPoint<T>{
    val x : T
    val y : T

    operator fun plus(obj : IPoint<T>) : IPoint<T>
    operator fun minus(obj : IPoint<T>) : IVector<T>

    operator fun plus(obj : IVector<T>) : IPoint<T>
    operator fun minus(obj : IVector<T>) : IPoint<T>

    fun distance(obj : IPoint<T>) : T
}

class DecartVector (override val x : Double, override val y : Double) : IVector<Double>{

    override val length : Double
        get() = Math.sqrt(x * x + y * y)

    override val angle : Double
        get() = Math.atan2(x, y)

    override fun plus(obj : IVector<Double>) = DecartVector(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IVector<Double>) = DecartVector(x = x - obj.x, y = y - obj.y)

    override fun times(scale : Double) = DecartVector(x = x * scale, y = y * scale)

    override fun times(vec : IVector<Double>) = x * vec.x + y * vec.y

    val normal : DecartVector
        get() = this * (1.0/Math.max(1.0, length))

    fun cos(vec : IVector<Double>) = (this * vec) / (length * vec.length)
}

class DecartPoint (override val x : Double, override val y : Double) : IPoint<Double> {

    override fun plus(obj : IPoint<Double>) = DecartPoint(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IPoint<Double>) = DecartVector(x = x - obj.x, y = y - obj.y)

    override fun plus(obj : IVector<Double>) = DecartPoint(x = x + obj.x, y = y + obj.y)

    override fun minus(obj : IVector<Double>) = DecartPoint(x = x - obj.x, y = y - obj.y)

    override fun distance(obj: IPoint<Double>): Double {
        val dx : Double = obj.x - x
        val dy : Double = obj.y - y
        return Math.sqrt(dx * dx + dy * dy)
    }

}


abstract class Object(val position : DecartPoint)

abstract class CircularObject(val id : String, position : DecartPoint, val weight : Double) : Object(position)

abstract class Player(id : String, position: DecartPoint, weight : Double, val radius :Double)
    : CircularObject(id, position, weight){

    fun maxSpeed() = consts!!.INERTION_FACTOR / weight * consts!!.SPEED_FACTOR

    fun splitDistance() = Math.abs(64 - maxSpeed() * maxSpeed())/(2 * consts!!.VISCOSITY)
}

class Food(position : DecartPoint) : Object(position)

class Ejection(position: DecartPoint, val pId : String) : Object(position)

class Virus(id : String, position: DecartPoint, weight : Double) : CircularObject(id, position, weight)

class Enemy(id : String, position: DecartPoint, weight : Double, radius :Double)
    : Player(id, position, weight, radius)

class Hero(id : String, position: DecartPoint, weight : Double, radius :Double, val speed : DecartVector)
    : Player(id, position, weight, radius)

fun getPosition(obj : JSONObject) = DecartPoint(obj["X"]!!.toString().toDouble(), obj["Y"]!!.toString().toDouble())

fun getSpeed(obj : JSONObject) = DecartVector(obj["SX"]!!.toString().toDouble(), obj["SY"]!!.toString().toDouble())


var GAME_WIDTH = 0.0
var GAME_HEIGHT = 0.0


interface IPotentialSource{
    val force : Double
    val position : DecartPoint

    fun effect(where : DecartPoint, hero: Hero) : Double
}

interface IPotential<T>{
    var potential : Double

    operator fun plusAssign(field : IPotentialSource)
    operator fun timesAssign(field : IPotentialSource)
}


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

class BorderField(override val force : Double, override val position: DecartPoint ) : IPotentialSource{

    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val distance = Math.max(1.0,
                if (position.x < 0)
                    Math.abs(position.y - where.y)
                else
                    Math.abs(position.x - where.x)
            )
        val direction = hero.speed

        return -force * force / hero.weight / (distance * distance)

    }
}

class FoodField(override val force : Double, food : Food) : IPotentialSource{

    override val position: DecartPoint = food.position

    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val offset = position - where
        val direction = hero.speed
        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))

        return force * consts!!.FOOD_MASS / hero.weight * (1.0 + cos) / ((offset.length + (position - hero.position).length) / 2)
    }
}

class EnemyField(override val force : Double, val enemy : Enemy) : IPotentialSource{

    override val position = enemy.position

    override fun effect(where : DecartPoint, hero: Hero) : Double{
        val offset = position - where
        val direction = hero.speed

        val cos = Math.max(1.0, direction * offset / Math.max(0.5, (direction.length * offset.length)))
        val distance = (offset.length + (position - hero.position).length) / 2

        if (1.2 * enemy.weight < hero.weight)
            return 4 * (1 + cos) * force * enemy.weight / hero.weight / distance


        val offsetHero = position - hero.position
        val nextDirection = where - hero.position
        val cos2 = nextDirection.cos(offsetHero)
        val sin = Math.sqrt(1 - cos2 * cos2)

        return if (enemy.weight< hero.weight * 1.2 ) -force / hero.weight / distance
        else -10  * force * enemy.weight / hero.weight / distance
    }

}


class PotentialObject(var toMove : DecartPoint, val heroes : List<Hero>) : IPotential<Double>{
    override var potential = 0.0

    override fun plusAssign(field: IPotentialSource){
        potential += heroes.map {
                val dir = (toMove - it.position).normal * consts!!.SPEED_FACTOR
            field.effect(it.position + dir, it)
        }.sum()
    }

    override fun timesAssign(field: IPotentialSource) {
        potential *= heroes.map {
            val dir = (toMove - it.position).normal * consts!!.SPEED_FACTOR
            field.effect(it.position + dir, it)
        }.sum()
    }
}

class PolarPotentialFields(val objects :List<PotentialObject>, val fields: List<IPotentialSource>){
    fun toMove() : DecartPoint{



        for (obj in objects){
            for (field in fields){
                obj += field
            }
        }

        return objects.maxBy { it.potential }!!.toMove
    }
}

class Consts(config : JSONObject){
    val SPEED_FACTOR  = config["SPEED_FACTOR"]!!.toString().toDouble()
    val GAME_WIDTH  = config["GAME_WIDTH"]!!.toString().toDouble()
    val GAME_HEIGHT  = config["GAME_HEIGHT"]!!.toString().toDouble()
    val FOOD_MASS = config["FOOD_MASS"]!!.toString().toDouble()
    val INERTION_FACTOR = config["INERTION_FACTOR"]!!.toString().toDouble()
    val VISCOSITY = config["VISCOSITY"]!!.toString().toDouble()
}

class World(heroes : JSONArray, objects : JSONArray, val consts : Consts){

    val heroes = heroes.map { it as JSONObject}.map {
        val id = it["Id"]!!.toString()
        val pos = getPosition(it)
        val speed = getSpeed(it)
        val w = it["M"]!!.toString().toDouble()
        val r = it["R"]!!.toString().toDouble()
        Hero(id = id, position =  pos, weight = w, radius = r, speed = speed)
    }
    val foods = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "F" }.map {
        Food(position = getPosition(it))
    }
    val ejetions = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "E" }.map {
        Ejection(position = getPosition(it), pId = it["pId"]!!.toString())
    }
    val viruses = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "V" }.map {
        Virus(id = it["Id"]!!.toString(), position = getPosition(it), weight = it["M"]!!.toString().toDouble())
    }
    val enemies = objects.map { it as JSONObject}.filter { it["T"]!!.toString() == "P" }.map {
        val id = it["Id"]!!.toString()
        val pos = getPosition(it)
        val w = it["M"]!!.toString().toDouble()
        val r = it["R"]!!.toString().toDouble()
        Enemy(id = id, position = pos, weight = w, radius = r)
    }

    fun maxHeroSize() = heroes.map{ it.weight }!!.max()!!
}

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
            HopeField(force = 70.0, nearDistance = 30.0)
    )
    val borders = listOf(
            BorderField(force = 100.0, position = DecartPoint(x = -1.0, y = 0.0)),
            BorderField(force = 100.0, position = DecartPoint(x = 0.0, y =  -1.0)),
            BorderField(force = 100.0, position = DecartPoint(x = -1.0, y = consts!!.GAME_HEIGHT)),
            BorderField(force = 100.0, position = DecartPoint(x = consts!!.GAME_WIDTH, y = -1.0))
    )


    private fun findFood() : Food? = world!!.foods.firstOrNull()

    private fun potentialObjects() =
            world!!.enemies.map { PotentialObject(it.position, world!!.heroes)} +
                    world!!.foods.take(10).map { PotentialObject(it.position, world!!.heroes)} +
                    hopes.map { PotentialObject(it.position, world!!.heroes)}

    override fun step(world : World) : StepInfo {
        this.world = world

        var needSpleet = world.maxHeroSize() > 120.0 && ( world.enemies.maxBy { it.weight }?.weight ?: 0.0) * 2.4 < world.maxHeroSize()

        if (!needSpleet)

            for (hero in world.heroes) {
                if (needSpleet) break
                for (enemy in world.enemies) {
                    if (needSpleet) break
                    if (enemy.weight * 2.5 < hero.weight && hero.speed.length > 0.5 &&
                            hero.speed.cos(enemy.position - hero.position) > 0.96 &&
                            (enemy.position - hero.position).length < 0.9 * hero.splitDistance()
                    ) needSpleet = true

                }
            }

        for (hope in hopes)
            for (hero in world.heroes)
                hope.check(hero.position)

        val fields = world.foods.map {FoodField(100.0, it)} +
                world.enemies.map {EnemyField(100.0, it)} +
                borders + hopes

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
        val world = World(mine, objects, consts!!)
        return strata!!.step(world).json
    }
    return JSONObject(mapOf("X" to 0, "Y" to 0, "Debug" to "Died"))
}

fun findFood(objects: JSONArray): JSONObject? =
        objects.map { it as JSONObject }.firstOrNull { it["T"] == "F" }