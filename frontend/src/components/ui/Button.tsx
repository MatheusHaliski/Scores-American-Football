"use client";
import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from "react";
import Link from "next/link";
import { cn } from "@/lib/utils/cn";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger" | "led" | "outline";
export type ButtonSize = "sm" | "md" | "lg" | "xl";

const variants: Record<ButtonVariant, string> = {
  primary: "bg-orange text-white hover:bg-rim active:bg-orange-deep shadow-[0_6px_18px_rgba(220,91,38,0.28)]",
  secondary: "bg-arena-3 text-text hover:bg-arena-4 border border-border-strong",
  ghost: "bg-transparent text-text-soft hover:bg-arena-3 hover:text-text",
  outline: "bg-transparent text-text border border-border-strong hover:border-rim hover:text-led",
  danger: "bg-bad/15 text-[var(--bad-text)] border border-bad/60 hover:bg-bad/30",
  led: "bg-pitch text-led border border-led/60 shadow-led hover:shadow-led-strong hover:border-led",
};
const sizes: Record<ButtonSize, string> = {
  sm: "h-9 px-3 text-[0.8rem] gap-1.5",
  md: "h-11 px-4 text-[0.9rem] gap-2",
  lg: "h-12 px-6 text-base gap-2",
  xl: "h-14 px-8 text-lg gap-3",
};

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant;
  size?: ButtonSize;
  loading?: boolean;
  icon?: ReactNode;
  iconRight?: ReactNode;
  block?: boolean;
  href?: string;
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = "primary", size = "md", loading, icon, iconRight, block, className, children, href, disabled, type = "button", ...rest },
  ref,
) {
  const cls = cn(
    "inline-flex items-center justify-center font-heading font-semibold uppercase tracking-[0.08em] rounded-[var(--radius-md)] select-none transition-[background,box-shadow,transform,border-color] duration-[var(--dur-base)] whitespace-nowrap",
    "disabled:opacity-45 disabled:cursor-not-allowed disabled:shadow-none active:scale-[0.99]",
    variants[variant],
    sizes[size],
    block && "w-full",
    className,
  );
  const inner = (
    <>
      {loading ? <span className="inline-block size-4 rounded-full border-2 border-current border-r-transparent animate-spin" aria-hidden /> : icon}
      {children && <span>{children}</span>}
      {iconRight}
    </>
  );
  if (href && !disabled) {
    return (
      <Link href={href} className={cls} aria-disabled={disabled}>
        {inner}
      </Link>
    );
  }
  return (
    <button ref={ref} type={type} className={cls} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {inner}
    </button>
  );
});
