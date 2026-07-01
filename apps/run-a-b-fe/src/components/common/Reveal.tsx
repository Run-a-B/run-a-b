import type { ReactNode } from "react";
import { useScrollReveal } from "@/hooks/useScrollReveal";

interface RevealProps {
  children: ReactNode;
  index?: number;
  className?: string;
}

export default function Reveal({ children, index = 0, className = "" }: RevealProps) {
  const { ref, visible } = useScrollReveal<HTMLDivElement>();

  return (
    <div
      ref={ref}
      className={`transition-[opacity,translate] duration-[900ms] ease-[cubic-bezier(0.16,1,0.3,1)] ${
        visible ? "opacity-100 translate-y-0" : "opacity-0 translate-y-10"
      } ${className}`}
      style={{ transitionDelay: visible ? `${index * 200}ms` : "0ms" }}
    >
      {children}
    </div>
  );
}
