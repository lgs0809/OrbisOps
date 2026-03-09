import React, { PropsWithChildren } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

export const appQueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: 1,
    },
    mutations: {
      retry: 0,
    },
  },
});

export const AppQueryProvider: React.FC<PropsWithChildren> = ({ children }) => (
  <QueryClientProvider client={appQueryClient}>{children}</QueryClientProvider>
);
