import { create } from 'zustand';
import { createJSONStorage, persist } from 'zustand/middleware';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Role, UserProfileResponse } from '@/types';
import { tokenStorage } from '@/store/tokenStorage';

interface AuthState {
  user: UserProfileResponse | null;
  token: string | null;
  isAuthenticated: boolean;
  role: Role | null;
  setSession: (token: string, user: UserProfileResponse) => void;
  setUser: (user: UserProfileResponse) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      user: null,
      token: null,
      isAuthenticated: false,
      role: null,
      setSession: (token, user) => {
        set({ token, user, isAuthenticated: true, role: user.role });
        void tokenStorage.set(token).catch(() => {});
      },
      setUser: (user) => set({ user, role: user.role }),
      logout: () => {
        set({ user: null, token: null, isAuthenticated: false, role: null });
        void tokenStorage.clear();
      },
    }),
    {
      name: 'bingo-mobile-auth',
      storage: createJSONStorage(() => AsyncStorage),
      partialize: (state) => ({
        user: state.user,
        token: state.token,
        isAuthenticated: state.isAuthenticated,
        role: state.role,
      }),
    }
  )
);