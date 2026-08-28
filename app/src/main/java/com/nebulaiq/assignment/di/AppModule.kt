package com.nebulaiq.assignment.di

import com.nebulaiq.assignment.data.auth.FirebaseAuthRepository
import com.nebulaiq.assignment.data.group.FirebaseGroupRepository
import com.nebulaiq.assignment.data.location.FusedLocationRepository
import com.nebulaiq.assignment.data.location.AndroidGeofenceRepository
import com.nebulaiq.assignment.data.messaging.FirebasePushTokenRepository
import com.nebulaiq.assignment.domain.repository.GeofenceRepository
import com.nebulaiq.assignment.domain.repository.PushTokenRepository
import com.nebulaiq.assignment.domain.repository.LocationRepository
import com.nebulaiq.assignment.domain.repository.AuthRepository
import com.nebulaiq.assignment.domain.repository.GroupRepository
import com.nebulaiq.assignment.presentation.group.GroupSetupViewModel
import com.nebulaiq.assignment.presentation.group.GroupTrackingViewModel
import com.nebulaiq.assignment.presentation.welcome.WelcomeViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single<AuthRepository> { FirebaseAuthRepository() }
    single<GroupRepository> { FirebaseGroupRepository() }
    single<LocationRepository> { FusedLocationRepository(get()) }
    single<GeofenceRepository> { AndroidGeofenceRepository(get()) }
    single<PushTokenRepository> { FirebasePushTokenRepository() }
    viewModel { WelcomeViewModel(get(), get()) }
    viewModel { GroupSetupViewModel(get(), get()) }
    viewModel { GroupTrackingViewModel(get(), get()) }
}
