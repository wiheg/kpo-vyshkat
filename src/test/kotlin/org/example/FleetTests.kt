package org.example

import kotlin.test.*

class FleetTests {
    private val repo = InMemoryFleetRepository()
    private val inspection = ServiceCenter()
    private val policy = DefaultBeginnerPolicy()
    private val service = FleetService(repo, inspection, policy)

    @Test
    fun `inspection approves sound and rejects defective`() {
        val sound = Kickscooter("K1", "S1", 8, 1.0)
        val broken = Bicycle("B1", "B1", 7, hasMechanicalDefect = true)
        assertTrue(inspection.inspect(sound).isApproved)
        assertFalse(inspection.inspect(broken).isApproved)
    }

    @Test
    fun `beginner policy checks threshold`() {
        val easy = Kickscooter("K1", "S1", 6, 1.0)
        val hard = Kickscooter("K2", "S2", 5, 1.0)
        assertTrue(policy.isSuitableForBeginners(easy))
        assertFalse(policy.isSuitableForBeginners(hard))
    }

    @Test
    fun `tryAcceptVehicle handles acceptance defect and duplicate`() {
        val v1 = Kickscooter("K1", "S1", 8, 1.0)
        val vDefect = ElectricBike("E1", "E1", 7, 2.0, hasMechanicalDefect = true)
        val vDup = Kickscooter("K1", "S1-Dup", 8, 1.0)

        assertTrue(service.tryAcceptVehicle(v1).isAccepted)
        assertFalse(service.tryAcceptVehicle(vDefect).isAccepted)
        assertFalse(service.tryAcceptVehicle(vDup).isAccepted)
    }

    @Test
    fun `registerItem handles items and duplicates`() {
        val h = Helmet("H1", "Helmet")
        val cs = ChargingStation("CS1", "Station", 5.0)
        val hDup = Helmet("H1", "Dup")

        assertTrue(service.registerItem(h).isAccepted)
        assertTrue(service.registerItem(cs).isAccepted)
        assertFalse(service.registerItem(hDup).isAccepted)
    }

    @Test
    fun `energy beginners inventory and stats`() {
        service.tryAcceptVehicle(Kickscooter("K2", "Scooter", 8, 1.5))
        service.tryAcceptVehicle(Bicycle("B1", "Bike", 4))
        service.registerItem(ChargingStation("CS1", "Station", 3.5))

        assertEquals(5.0, service.getTotalDailyEnergyConsumptionKWh())
        assertEquals(listOf("K2"), service.getVehiclesForBeginners().map { it.inventoryNumber })
        assertEquals(listOf("B1", "CS1", "K2"), service.getAllInventory().map { it.inventoryNumber })

        val stats = service.getFleetStats()
        assertEquals(3, stats.totalItems)
        assertEquals(2, stats.vehicleCount)
        assertEquals(1, stats.otherItemCount)
        assertEquals(5.0, stats.totalDailyEnergyKWh)
    }

    @Test
    fun `di container resolves singletons transients and stub override`() {
        val container = DiContainer().apply {
            registerSingleton { InMemoryFleetRepository() as IFleetRepository }
            registerTransient { ServiceCenter() as ITechnicalInspectionService }
        }
        assertSame(container.resolve<IFleetRepository>(), container.resolve<IFleetRepository>())
        assertNotSame(
            container.resolve<ITechnicalInspectionService>(),
            container.resolve<ITechnicalInspectionService>()
        )

        val stubContainer = configureContainer(object : ITechnicalInspectionService {
            override fun inspect(vehicle: IVehicle) = InspectionResult(false, "stub reject")
        })
        val stubService = stubContainer.resolve<IFleetService>()
        val res = stubService.tryAcceptVehicle(Kickscooter("K1", "S1", 10, 1.0))
        assertFalse(res.isAccepted)
        assertTrue(res.message.contains("stub reject"))
    }

    @Test
    fun `main execution smoke test`() {
        main()
    }
}
