import { useCallback, useEffect, useState } from 'react';

const MOBILE_SIDEBAR_QUERY = '(max-width: 768px)';

const matchesMobileViewport = () =>
  typeof window !== 'undefined' && window.matchMedia(MOBILE_SIDEBAR_QUERY).matches;

export const useResponsiveSidebar = (defaultCollapsed?: boolean) => {
  const [mobile, setMobile] = useState(matchesMobileViewport);
  const [desktopCollapsed, setDesktopCollapsed] = useState(() =>
    defaultCollapsed ?? (typeof window !== 'undefined' && window.innerWidth < 960),
  );
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const query = window.matchMedia(MOBILE_SIDEBAR_QUERY);
    const handleChange = (event: MediaQueryListEvent | MediaQueryList) => {
      setMobile(event.matches);
      setMobileOpen(false);
    };

    handleChange(query);
    query.addEventListener('change', handleChange);
    return () => query.removeEventListener('change', handleChange);
  }, []);

  const toggleSidebar = useCallback(() => {
    if (mobile) {
      setMobileOpen((value) => !value);
      return;
    }
    setDesktopCollapsed((value) => !value);
  }, [mobile]);

  const closeMobileSidebar = useCallback(() => setMobileOpen(false), []);

  return {
    mobile,
    mobileOpen,
    collapsed: desktopCollapsed,
    sidebarCollapsed: mobile ? !mobileOpen : desktopCollapsed,
    toggleSidebar,
    closeMobileSidebar,
  };
};
