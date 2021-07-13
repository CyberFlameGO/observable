package observable

import com.mojang.blaze3d.platform.InputConstants
import me.shedaniel.architectury.event.events.GuiEvent
import me.shedaniel.architectury.event.events.LifecycleEvent
import me.shedaniel.architectury.event.events.PlayerEvent
import me.shedaniel.architectury.event.events.client.ClientRawInputEvent
import me.shedaniel.architectury.event.events.client.ClientTickEvent
import me.shedaniel.architectury.networking.NetworkChannel
import me.shedaniel.architectury.networking.NetworkManager
import me.shedaniel.architectury.registry.CreativeTabs
import me.shedaniel.architectury.registry.DeferredRegister
import me.shedaniel.architectury.registry.KeyBindings
import me.shedaniel.architectury.registry.Registries
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.network.chat.TextComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.LazyLoadedValue
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import observable.client.ProfileScreen
import observable.net.BetterChannel
import observable.server.C2SPacket
import observable.server.Profiler
import observable.server.S2CPacket
import org.apache.logging.log4j.LogManager
import org.lwjgl.glfw.GLFW
import java.io.Serializable
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.roundToInt

object Observable {
    const val MOD_ID = "observable"

    // We can use this if we don't want to use DeferredRegister
    val REGISTRIES = LazyLoadedValue { Registries.get(MOD_ID) }

    // Registering a new creative tab
//    val EXAMPLE_TAB: CreativeModeTab = CreativeTabs.create(ResourceLocation(MOD_ID, "example_tab")) { ItemStack(EXAMPLE_ITEM.get()) }
//    val ITEMS = DeferredRegister.create(MOD_ID, Registry.ITEM_REGISTRY)
//    val EXAMPLE_ITEM = ITEMS.register("example_item") { Item(Item.Properties().tab(Observable.EXAMPLE_TAB)) }

    val PROFILE_KEYBIND = KeyMapping("key.observable.profile",
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "category.observable.keybinds")

    val CHANNEL = BetterChannel(ResourceLocation("channel/observable"))

    val LOGGER = LogManager.getLogger("Observable")

    var PROFILER: Profiler? = null

    @JvmStatic
    fun init() {
        CHANNEL.register { t: C2SPacket.InitTPSProfile, supplier ->
            PROFILER?.startRunning(t.duration)
        }

        CHANNEL.register { t: S2CPacket.ProfilingResult, supplier ->
            val data = t.data.data
            LOGGER.info("Received profiling result with ${data.size} entries")
            data.slice(0..5).withIndex().forEach { (idx, v) ->
                val (obj, timingData) = v
                val className =
                        obj.entity?.let { it.javaClass.name } ?:
                        obj.blockEntity?.let { it.javaClass.name } ?:
                        "Unknown class"
                LOGGER.info("$idx: $className -- ${(timingData.rate / 1000).roundToInt()} us/t")
            }
        }

        LifecycleEvent.SERVER_WORLD_LOAD.register {
            PROFILER = Profiler(it)
            LOGGER.info("Loaded profiler")
        }

        LifecycleEvent.SERVER_WORLD_UNLOAD.register {
            PROFILER = null
            LOGGER.info("Unloaded profiler")
        }

        PlayerEvent.PLAYER_JOIN.register {
            PROFILER?.players?.add(it)
            val name = (it.name as TextComponent).text
            LOGGER.info("$name joined")
        }

        PlayerEvent.PLAYER_QUIT.register {
            PROFILER?.players?.remove(it)
            val name = (it.name as TextComponent).text
            LOGGER.info("$name left")
        }
    }

    @JvmStatic
    fun clientInit() {
        KeyBindings.registerKeyBinding(PROFILE_KEYBIND)

        ClientTickEvent.CLIENT_POST.register {
            if (PROFILE_KEYBIND.consumeClick()) {
                it.setScreen(ProfileScreen())
            }
        }

        GuiEvent.RENDER_HUD.register { stack, v ->

        }
    }
}