import { CareerShell } from "@/components/shell/CareerShell";

/** Shell da franquia (barra lateral das seções) para todas as telas de /c/[careerId]. */
export default async function CareerLayout({ children, params }: { children: React.ReactNode; params: Promise<{ careerId: string }> }) {
  const { careerId } = await params;
  return <CareerShell careerId={careerId}>{children}</CareerShell>;
}
