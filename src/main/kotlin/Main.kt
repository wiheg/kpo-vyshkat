package org.example

import kotlin.reflect.KClass

// интерфейсы
interface IInventoryItem {
    val inventoryNumber: String;
    val title: String
}

interface IEnergyConsumer {
    val dailyEnergyConsumptionKWh: Double
}

interface IVehicle : IInventoryItem {
    val beginnerFriendliness: Int
    val hasMechanicalDefect: Boolean
    val vehicleType: String
}

// транспорт и вещи
class Kickscooter(
    override val inventoryNumber: String,
    override val title: String,
    override val beginnerFriendliness: Int,
    override val dailyEnergyConsumptionKWh: Double,
    override val hasMechanicalDefect: Boolean = false
) : IVehicle, IEnergyConsumer {
    override val vehicleType = "электросамокат"
}

class Bicycle(
    override val inventoryNumber: String,
    override val title: String,
    override val beginnerFriendliness: Int,
    override val hasMechanicalDefect: Boolean = false
) : IVehicle {
    override val vehicleType = "велосипед"
}

class ElectricBike(
    override val inventoryNumber: String,
    override val title: String,
    override val beginnerFriendliness: Int,
    override val dailyEnergyConsumptionKWh: Double,
    override val hasMechanicalDefect: Boolean = false
) : IVehicle, IEnergyConsumer {
    override val vehicleType = "электровелосипед"
}

class Helmet(override val inventoryNumber: String, override val title: String) : IInventoryItem
class ChargingStation(
    override val inventoryNumber: String,
    override val title: String,
    override val dailyEnergyConsumptionKWh: Double
) : IInventoryItem, IEnergyConsumer

// техосмотр и правила
data class InspectionResult(val isApproved: Boolean, val reason: String)
data class AcceptanceResult(val isAccepted: Boolean, val message: String)

interface ITechnicalInspectionService {
    fun inspect(vehicle: IVehicle): InspectionResult
}

class ServiceCenter : ITechnicalInspectionService {
    override fun inspect(vehicle: IVehicle) =
        if (vehicle.hasMechanicalDefect) InspectionResult(false, "обнаружена поломка")
        else InspectionResult(true, "техосмотр пройден успешно")
}

interface IBeginnerPolicy {
    fun isSuitableForBeginners(vehicle: IVehicle): Boolean
}

class DefaultBeginnerPolicy(private val minFriendliness: Int = 6) : IBeginnerPolicy {
    override fun isSuitableForBeginners(vehicle: IVehicle) = vehicle.beginnerFriendliness >= minFriendliness
}

// репозиторий
interface IFleetRepository {
    fun add(item: IInventoryItem)
    fun getAll(): List<IInventoryItem>
    fun getVehicles(): List<IVehicle>
    fun exists(inventoryNumber: String): Boolean
}

class InMemoryFleetRepository : IFleetRepository {
    private val items = mutableMapOf<String, IInventoryItem>()
    override fun add(item: IInventoryItem) {
        items[item.inventoryNumber] = item
    }

    override fun getAll(): List<IInventoryItem> = items.values.toList()
    override fun getVehicles(): List<IVehicle> = items.values.filterIsInstance<IVehicle>()
    override fun exists(inventoryNumber: String) = items.containsKey(inventoryNumber)
}

// сервис флота
data class FleetStats(
    val totalItems: Int,
    val vehicleCount: Int,
    val otherItemCount: Int,
    val totalDailyEnergyKWh: Double
)

interface IFleetService {
    fun tryAcceptVehicle(vehicle: IVehicle): AcceptanceResult
    fun registerItem(item: IInventoryItem): AcceptanceResult
    fun getTotalDailyEnergyConsumptionKWh(): Double
    fun getVehiclesForBeginners(): List<IVehicle>
    fun getAllInventory(): List<IInventoryItem>
    fun getFleetStats(): FleetStats
}

class FleetService(
    private val repository: IFleetRepository,
    private val inspectionService: ITechnicalInspectionService,
    private val beginnerPolicy: IBeginnerPolicy
) : IFleetService {
    override fun tryAcceptVehicle(vehicle: IVehicle): AcceptanceResult {
        if (repository.exists(vehicle.inventoryNumber)) return AcceptanceResult(
            false,
            "дубликат номера '${vehicle.inventoryNumber}'"
        )
        val inspection = inspectionService.inspect(vehicle)
        if (!inspection.isApproved) return AcceptanceResult(false, "отклонено: ${inspection.reason}")
        repository.add(vehicle)
        return AcceptanceResult(true, "принято: ${inspection.reason}")
    }

    override fun registerItem(item: IInventoryItem): AcceptanceResult {
        if (repository.exists(item.inventoryNumber)) return AcceptanceResult(
            false,
            "дубликат номера '${item.inventoryNumber}'"
        )
        repository.add(item)
        return AcceptanceResult(true, "вещь поставлена на баланс")
    }

    override fun getTotalDailyEnergyConsumptionKWh(): Double =
        repository.getAll().filterIsInstance<IEnergyConsumer>().sumOf { it.dailyEnergyConsumptionKWh }

    override fun getVehiclesForBeginners(): List<IVehicle> =
        repository.getVehicles().filter { beginnerPolicy.isSuitableForBeginners(it) }

    override fun getAllInventory(): List<IInventoryItem> = repository.getAll().sortedBy { it.inventoryNumber }

    override fun getFleetStats(): FleetStats {
        val all = repository.getAll()
        val vehicles = all.filterIsInstance<IVehicle>().size
        return FleetStats(all.size, vehicles, all.size - vehicles, getTotalDailyEnergyConsumptionKWh())
    }
}

// di контейнер
class DiContainer {
    val bindings = mutableMapOf<KClass<*>, () -> Any>()
    inline fun <reified T : Any> registerTransient(noinline factory: DiContainer.() -> T) {
        bindings[T::class] = { factory() }
    }

    inline fun <reified T : Any> registerSingleton(noinline factory: DiContainer.() -> T) {
        val instance by lazy { factory() }
        bindings[T::class] = { instance }
    }

    inline fun <reified T : Any> resolve(): T =
        (bindings[T::class]?.invoke() ?: error("зависимость ${T::class.simpleName} не найдена")) as T
}

fun configureContainer(inspectionOverride: ITechnicalInspectionService? = null) = DiContainer().apply {
    registerSingleton<IFleetRepository> { InMemoryFleetRepository() }
    registerTransient<ITechnicalInspectionService> { inspectionOverride ?: ServiceCenter() }
    registerTransient<IBeginnerPolicy> { DefaultBeginnerPolicy() }
    registerTransient<IFleetService> { FleetService(resolve(), resolve(), resolve()) }
}

fun main() {
    System.setOut(java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8))
    val fleet = configureContainer().resolve<IFleetService>()
    println("ВЫШКАТ: СИСТЕМА УЧЁТА ПАРКА")

    println("\n[1] Приёмка транспорта через техосмотр:")
    listOf(
        Kickscooter("KS-101", "Ninebot Max G30", 8, 1.25),
        Bicycle("BC-201", "Forward Sporting", 9),
        Kickscooter("KS-777", "Dualtron Pro", 4, 3.2),
        ElectricBike("EB-999", "Broken Cruiser", 5, 1.5, hasMechanicalDefect = true),
        Kickscooter("KS-101", "Ninebot Max G30 (дубликат)", 8, 1.25)
    ).forEach { v ->
        val res = fleet.tryAcceptVehicle(v)
        val tag = if (res.isAccepted) "[ПРИНЯТО]  " else "[ОТКЛОНЕНО]"
        println("  $tag [${v.inventoryNumber}] ${v.title} — ${res.message}")
    }

    println("\n[2] Постановка имущества на баланс:")
    listOf(
        Helmet("HLM-001", "Шлем SafeRide L"),
        ChargingStation("CHG-010", "Зарядный шкаф Покровский б-р", 8.4),
        Helmet("HLM-001", "Шлем SafeRide L (дубликат)")
    ).forEach { item ->
        val res = fleet.registerItem(item)
        val tag = if (res.isAccepted) "[ПРИНЯТО]  " else "[ОТКЛОНЕНО]"
        println("  $tag [${item.inventoryNumber}] ${item.title} — ${res.message}")
    }

    println("\n[3] Отчёт по числу единиц и суммарной энергии:")
    val stats = fleet.getFleetStats()
    println("  Всего на балансе: ${stats.totalItems} ед. (транспорт: ${stats.vehicleCount}, вещи: ${stats.otherItemCount})")
    println("  Суточный расход электроэнергии: ${"%.2f".format(stats.totalDailyEnergyKWh)} кВт·ч")

    println("\n[4] Список транспорта для новичков (дружелюбность >= 6):")
    fleet.getVehiclesForBeginners().forEach {
        println("  [${it.inventoryNumber}] ${it.title} | тип: ${it.vehicleType} | дружелюбность: ${it.beginnerFriendliness}/10")
    }

    println("\n[5] Полный реестр инвентарных номеров:")
    println("  %-10s | %-32s | %-18s\n  ${"-".repeat(66)}".format("Инв. №", "Наименование", "Тип"))
    fleet.getAllInventory().forEach { item ->
        val type = when (item) {
            is IVehicle -> item.vehicleType
            is ChargingStation -> "зарядная станция"
            is Helmet -> "шлем"
            else -> "инвентарь"
        }
        println("  %-10s | %-32s | %-18s".format(item.inventoryNumber, item.title, type))
    }

    println("\n[6] Демонстрация подмены техосмотра через DI (тестовая заглушка):")
    val testFleet = configureContainer(object : ITechnicalInspectionService {
        override fun inspect(vehicle: IVehicle) = InspectionResult(false, "тестовая заглушка: сервисный центр закрыт")
    }).resolve<IFleetService>()
    val testRes = testFleet.tryAcceptVehicle(Kickscooter("KS-TST", "Тестовый самокат", 10, 1.0))
    println("  Результат с подменённым ServiceCenter: ${testRes.message}\n" + "=".repeat(68))
}
