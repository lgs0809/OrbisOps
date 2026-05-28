export type ProjectProductView = 'overview' | 'resources' | 'capabilities' | 'members' | 'runtime';
const views: ProjectProductView[] = ['overview', 'resources', 'capabilities', 'members', 'runtime'];

export const projectProductViewFor = (view: string | null, pathname: string): ProjectProductView => (
  views.includes(view as ProjectProductView) ? view as ProjectProductView
    : pathname === '/projects/advanced' ? 'runtime' : 'overview'
);
