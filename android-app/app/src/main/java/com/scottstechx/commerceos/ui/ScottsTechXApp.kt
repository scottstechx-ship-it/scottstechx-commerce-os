package com.scottstechx.commerceos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scottstechx.commerceos.data.auth.AuthStore
import com.scottstechx.commerceos.data.auth.Role
import com.scottstechx.commerceos.ui.ai.CustomerChatScreen
import com.scottstechx.commerceos.ui.ai.SellerAssistantScreen
import com.scottstechx.commerceos.ui.buyer.BuyerScreen
import com.scottstechx.commerceos.ui.driver.DriverScreen
import com.scottstechx.commerceos.ui.login.LoginScreen
import com.scottstechx.commerceos.ui.nearby.NearbySellersScreen
import com.scottstechx.commerceos.ui.seller.SellerDetailScreen
import com.scottstechx.commerceos.ui.seller.SellerScreen

object Routes {
    const val LOGIN = "login"
    const val BUYER = "buyer"
    const val SELLER = "seller"
    const val DRIVER = "driver"
    const val NEARBY_SELLERS = "nearby"
    const val SELLER_DETAIL = "seller/{sellerId}"
    const val AI_SELLER = "ai/seller"
    const val AI_CHAT = "ai/chat"

    fun sellerDetail(sellerId: String): String = "seller/$sellerId"
}

@Composable
fun ScottsTechXApp(
    authStore: AuthStore = hiltViewModel<AuthGateViewModel>().authStore
) {
    val navController = rememberNavController()
    val authState by authStore.state.collectAsState()

    val startDestination = when {
        !authState.token.isNullOrBlank() && authState.role == Role.SELLER -> Routes.SELLER
        !authState.token.isNullOrBlank() && authState.role == Role.BUYER -> Routes.BUYER
        !authState.token.isNullOrBlank() && authState.role == Role.DRIVER -> Routes.DRIVER
        else -> Routes.LOGIN
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = com.scottstechx.commerceos.ui.animation.NavTransitions.enter(),
        exitTransition = com.scottstechx.commerceos.ui.animation.NavTransitions.exit(),
        popEnterTransition = com.scottstechx.commerceos.ui.animation.NavTransitions.popEnter(),
        popExitTransition = com.scottstechx.commerceos.ui.animation.NavTransitions.popExit()
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onSignedIn = { role ->
                    val target = when (role) {
                        Role.SELLER -> Routes.SELLER
                        Role.DRIVER -> Routes.DRIVER
                        else -> Routes.BUYER
                    }
                    navController.navigate(target) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.BUYER) {
            BuyerScreen(
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                },
                onOpenNearby = {
                    navController.navigate(Routes.NEARBY_SELLERS)
                },
                onOpenChat = {
                    navController.navigate(Routes.AI_CHAT)
                }
            )
        }
        composable(Routes.SELLER) {
            SellerScreen(
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                },
                onOpenAssistant = {
                    navController.navigate(Routes.AI_SELLER)
                }
            )
        }
        composable(Routes.DRIVER) {
            DriverScreen(
                onSignedOut = {
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                }
            )
        }
        composable(Routes.NEARBY_SELLERS) {
            NearbySellersScreen(
                onBack = { navController.popBackStack() },
                onSellerClick = { sellerId ->
                    navController.navigate(Routes.sellerDetail(sellerId))
                }
            )
        }
        composable(
            Routes.SELLER_DETAIL,
            arguments = listOf(navArgument("sellerId") { type = NavType.StringType })
        ) {
            SellerDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_SELLER) {
            SellerAssistantScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_CHAT) {
            CustomerChatScreen(onBack = { navController.popBackStack() })
        }
    }
}
